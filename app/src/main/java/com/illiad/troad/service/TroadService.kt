package com.illiad.troad.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
// Import the normal legacy color system but rename it to AndroidColor
import android.graphics.Color as AndroidColor
// Import the modern Compose color system normally
// import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import com.illiad.troad.Consts.ACTION_VPN_STATUS_BROADCAST
import com.illiad.troad.Consts.ACTION_CONNECT
import com.illiad.troad.Consts.ACTION_DISCONNECT
import com.illiad.troad.Consts.ACTION_RESTART
import com.illiad.troad.Consts.EXTRA_IS_CONNECTED
import com.illiad.troad.Consts.EXTRA_STATUS_MESSAGE
import com.illiad.troad.Consts.MTU
import com.illiad.troad.Consts.NOTIFICATION_CHANNEL_ID
import com.illiad.troad.Consts.NOTIFICATION_CHANNEL_NAME
import com.illiad.troad.Consts.NOTIFICATION_ID
import com.illiad.troad.Consts.PENDING_INTENT_REQUEST_CODE_DISCONNECT
import com.illiad.troad.Consts.TS
import com.illiad.troad.Consts.tunIp10_8_0_2
import com.illiad.troad.MainActivity
import com.illiad.troad.R
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.security.HeaderEncoder
import com.illiad.troad.service.security.client.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import troadengine.Troadengine
import java.io.File

@SuppressLint("VpnServicePolicy")
class TroadService : VpnService() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TS, "Unhandled exception in TroadService scope", throwable)
        // Instead of silent termination, we handle it as a software failure and stop the service gracefully.
        handleSoftwareFailure("Unexpected background error: ${throwable.localizedMessage}")
    }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    private val tStore by lazy { TroadStore(applicationContext) }

    // 1. Lazy initialize the singleton TokenManager
    private val tokenManager by lazy { TokenManager.getInstance(applicationContext) }

    // Injected via Hilt/Koin or instantiated manually
    private val settingsUseCase: SettingsUseCase = SettingsUseCase(SettingsRepoImp(tStore))
    private var activeSettings: Settings? = null
    private var header: String? = null

    @Volatile
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeSettings()
        Log.d(TS, "VPN Service Created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> handleConnect()
            ACTION_DISCONNECT -> stopVpn()
            ACTION_RESTART -> handleRestart() // Direct intent control route
        }
        return START_STICKY
    }

    private fun handleConnect() {
        if (vpnInterface != null) return
        startForeground(
            NOTIFICATION_ID,
            createNotification(
                "Connecting...",
                R.drawable.ic_vpn_on,
                AndroidColor.parseColor("#0284C7")
            )
        )

        vpnJob = serviceScope.launch {
            try {
                val currentVpnInterface = establishVpnInterfaceReturn()
                if (currentVpnInterface != null) {
                    vpnInterface = currentVpnInterface
                    runVpnStack(currentVpnInterface.fd)
                    updateNotification(
                        "VPN Active",
                        R.drawable.ic_vpn_on,
                        AndroidColor.parseColor("#FFE4A7")
                    )
                    broadcastStatus("Connected", true)
                } else {
                    // CONNECTION FAIL: System couldn't establish the interface (e.g., restricted profile)
                    handleConnectionFailure("Failed to allocate secure interface.")
                }
            } catch (e: Exception) {
                // SOFTWARE FAIL: Complete failure inside setup coroutines
                Log.e(TS, "Fatal Internal Software Crash", e)
                terminateEntireAppSilently()
            }
        }
    }

    private fun establishVpnInterfaceReturn(): ParcelFileDescriptor? {
        return try {
            Builder()
                .setSession(getString(R.string.app_name))
                .addAddress(tunIp10_8_0_2, 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("223.5.5.5")
                .addDnsServer("223.6.6.6")
                //.addDnsServer(DNS8888)
                //.addDnsServer(DNS1001)
                .addDisallowedApplication(packageName)
                .setMtu(MTU)
                .establish()
        } catch (e: Exception) {
            Log.e(TS, "Vpn Builder failed", e)
            null
        }
    }


    /**
     * Executes the native tun2socks engine.
     * This function suspends until the VPN is stopped or the coroutine is cancelled.
     */

    private fun runVpnStack(fd: Int) {
        Thread({
            try {
                val currentSettings =
                    activeSettings ?: throw IllegalStateException("VPN Settings not loaded.")
                val currentHeader =
                    header ?: throw IllegalStateException("Security Header not initialized.")

                // SOFTWARE FAIL CHECK: Validate file system write health immediately
                val certFile = File(applicationContext.cacheDir, "proxy_ca.crt")
                currentSettings.cacert?.let { certFile.writeText(it) }

                Log.i(TS, "Go Engine Thread Started")

                // blocks here until stopped or network pipe disconnects
                Troadengine.startTroad(
                    fd.toLong(),
                    currentSettings.domain + ":" + currentSettings.port.toString(),
                    currentHeader,
                    certFile.absolutePath,
                    currentSettings.sni,
                    MTU.toLong()
                )

                Log.i(TS, "Go Engine Thread Exited Normally")
            } catch (e: IllegalStateException) {
                // SOFTWARE FAIL: Missing assets or native library linking failures
                Log.e(TS, "Software Setup Aborted: ${e.message}")
                val displayMsg = e.message ?: "Software Setup Error"
                Handler(Looper.getMainLooper()).post { handleSoftwareFailure(displayMsg) }
            } catch (e: Throwable) {
                // CONNECTION FAIL: Remote server closed, timeout, packet loss, or bad handshake
                Log.e(TS, "Remote Connection Dropped/Failed: ${e.message}")
                Handler(Looper.getMainLooper()).post {
                    handleConnectionFailure("Server Connection Dropped. Retrying...")
                }
            }
        }, "GoEngineThread").start()
    }

    private fun stopVpn() {
        broadcastStatus("Disconnected", false)
        vpnJob?.cancel()

        serviceScope.launch(Dispatchers.Main) {
            try {
                vpnInterface?.close()
            } catch (e: IOException) {
                Log.e(TS, "Close error", e)
            } finally {
                vpnInterface = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /**
     * CONNECTION FAIL HANDLER: Holds the app process alive in memory space,
     * resets UI layouts safely to Disconnected states, and alerts the client.
     */
    private fun handleConnectionFailure(errorMessage: String) {
        // 1. Clean up active file tunnels but keep the background service context alive
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TS, "Quiet close failure", e)
        }
        vpnInterface = null
        vpnJob?.cancel()

        // 2. Broadcast the error message to MainViewModel to flash the screen layout
        broadcastStatus(errorMessage, false)

        // 3. Demote notification back to a static, persistent "Disconnected/Idle" icon
        // This alerts the user while keeping the software running smoothly in the background
        updateNotification(
            "Connection Failed. Tap to reconnect.",
            R.drawable.ic_vpn_off,
            AndroidColor.parseColor("#F1F5F9")
        )
    }

    /**
     * SOFTWARE FAIL HANDLER: Safely shuts down the service due to internal errors (like missing certs),
     * without killing the whole application process.
     */
    private fun handleSoftwareFailure(errorMessage: String) {
        Log.e(TS, "Software Failure: $errorMessage")

        // 1. Broadcast the error to UI
        broadcastStatus(errorMessage, false)

        // 2. Clean up local tunnel resources
        vpnJob?.cancel()
        try {
            vpnInterface?.close()
        } catch (e: Exception) { /* no-op */
        }
        vpnInterface = null

        // 3. Update notification to error state
        updateNotification(
            "Service Error: Tap to check settings.",
            R.drawable.ic_vpn_off,
            AndroidColor.parseColor("#F1F5F9")
        )

        // 4. Stop the service only
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Legacy helper - now redirects to handleSoftwareFailure to prevent app exit
     */
    private fun terminateEntireAppSilently() {
        handleSoftwareFailure("Internal Application Error")
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsUseCase()
                .catch { e ->
                    Log.e("VpnService", "Failed to resolve clean VPN snapshot", e)
                    throw IllegalStateException("Failed to resolve clean VPN snapshot.")
                }
                .collectLatest { validSnapshot ->
                    applySettings(validSnapshot)
                }
        }
    }

    private fun applySettings(newSettings: Settings) {
        val previousSettings = activeSettings
        if (newSettings == previousSettings) return // Optimization: Nothing changed

        Log.d(
            "VpnService",
            "Applying fresh atomic config snapshot for domain: ${newSettings.domain}"
        )

        // 1. Keep background token auto-renew loops perfectly synced
        tokenManager.processSettingsUpdate(newSettings)

        if (newSettings.crypto != previousSettings?.crypto || newSettings.jwt != previousSettings.jwt || newSettings.secret != previousSettings.secret) {
            Log.i(
                "VpnService",
                "Credential rotation detected (Crypto/JWT/Secret updated). Re-encoding connection headers."
            )
            // Generate the fresh binary/string connection token layout
            header = HeaderEncoder.encodeHeader(
                newSettings.crypto,
                newSettings.secret,
                newSettings.jwt
            )

        }

        // Cache the snapshot after handling soft changes
        activeSettings = newSettings


        if (previousSettings == null ||
            previousSettings.domain != newSettings.domain ||
            previousSettings.port != newSettings.port ||
            previousSettings.cacert != newSettings.cacert
        ) {

            handleRestart()
        }
    }


    private fun handleRestart() {
        Log.d(TS, "Executing atomic VPN tunnel hot-restart...")

        // 1. Cancel the active reading/writing coroutine job immediately
        vpnJob?.cancel()

        // 2. Safely close the existing system TUN interface and file descriptor
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TS, "Error closing interface during restart", e)
        }
        vpnInterface = null

        // 3. Update notification state so user knows a configuration shift is happening
        updateNotification(
            "Reconnecting...",
            R.drawable.ic_vpn_on,
            AndroidColor.parseColor("#0284C7")
        )
        broadcastStatus("Reconnecting", false)

        // 4. Launch a brand new connection sequence exactly like handleConnect()
        vpnJob = serviceScope.launch {
            try {
                val currentVpnInterface = establishVpnInterfaceReturn()
                if (currentVpnInterface != null) {
                    vpnInterface = currentVpnInterface

                    // Pass the fresh file descriptor down to your native engine/stack
                    runVpnStack(currentVpnInterface.fd)

                    updateNotification(
                        "VPN Active",
                        R.drawable.ic_vpn_on,
                        AndroidColor.parseColor("#FFE4A7")
                    )
                    broadcastStatus("Connected", true)
                } else {
                    handleConnectionFailure("Failed to re-allocate secure interface.")
                }
            } catch (e: Exception) {
                Log.e(TS, "Fatal Internal Software Crash during restart", e)
                terminateEntireAppSilently()
            }
        }
    }

    private fun broadcastStatus(message: String, isConnected: Boolean) {
        sendBroadcast(Intent(ACTION_VPN_STATUS_BROADCAST).apply {
            putExtra(EXTRA_STATUS_MESSAGE, message)
            putExtra(EXTRA_IS_CONNECTED, isConnected)
            setPackage(packageName)
        })
    }

    private fun updateNotification(text: String, iconResId: Int, statusColor: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createNotification(text, iconResId, statusColor))
    }

    /**
     * Generates an optimized, low-overhead system notification layout profile.
     *
     * @param text The status description message string to write out inside the panel drawer.
     * @param iconResId The target asset file reference (Offline layout line art vs Active bold fill).
     * @param statusColor The accent color to apply to the notification UI.
     */
    private fun createNotification(text: String, iconResId: Int, statusColor: Int): Notification {
        val pendingIntent = { action: String, code: Int ->
            val intent = Intent(
                this,
                if (action == ACTION_DISCONNECT) TroadService::class.java else MainActivity::class.java
            )
            intent.action = action
            PendingIntent.getService(this, code, intent, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            // Set both small and large to the identical token asset id
            // This ensures the status bar line and expanded tray panel sync status identically
            .setSmallIcon(iconResId)
            .setColor(statusColor)
            .setColorized(true)
            .setContentTitle("Troad VPN")
            .setContentText(text)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_vpn_off,
                "Disconnect",
                pendingIntent(ACTION_DISCONNECT, PENDING_INTENT_REQUEST_CODE_DISCONNECT)
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        Log.w(
            TS,
            "VPN Service is being permanently destroyed by the system wrapper. Cleaning resources..."
        )

        // 1. Instantly alert your frontend UI screens that the tunnel is dead
        // This forces the SmartStateSailLogo back to its default Disconnected state
        try {
            broadcastStatus("Disconnected", false)
        } catch (e: Exception) {
            Log.e(TS, "Failed to broadcast final destruction exit loop status", e)
        }

        // 2. Critical Network Safety Release: Tear down the interface channel
        // If you skip this, the user's phone will lose all internet connectivity after the app exits!
        try {
            vpnInterface?.close()
            Log.i(TS, "Secure VPN tunnel interface file descriptor closed successfully.")
        } catch (e: Exception) {
            Log.e(TS, "Error forcing final close on VPN interface", e)
        } finally {
            vpnInterface = null
        }

        // 3. Force cancel your long-running Go Engine tasks and Coroutine workers
        vpnJob?.cancel()
        serviceScope.cancel()

        // 4. Clean exit via parent framework call
        super.onDestroy()
    }

}
