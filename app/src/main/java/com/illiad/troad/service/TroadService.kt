package com.illiad.troad.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.illiad.troad.Consts.ACTION_CONNECT
import com.illiad.troad.Consts.ACTION_DISCONNECT
import com.illiad.troad.Consts.ACTION_RESTART
import com.illiad.troad.Consts.ACTION_VPN_STATUS_BROADCAST
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import troadengine.Troadengine
import java.io.File
import java.io.IOException

// 1. CHANGE INHERITANCE: Must be VpnService, manually providing LifecycleOwner
class TroadService : VpnService(), LifecycleOwner, Butler.TunnelInterfaceController {

    // 2. MANUALLY IMPLEMENT LIFECYCLE CONTAINER: Provides 'lifecycleScope' safely
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val butler by lazy { Butler.getInstance(applicationContext) }
    private var vpnJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // Bind your clean Butler orchestration component
        butler.startMonitoring(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        when (intent?.action) {
            ACTION_CONNECT -> handleConnect()
            ACTION_DISCONNECT -> stopVpn()
            ACTION_RESTART -> {
                val currentHeader = butler.header ?: byteArrayOf()
                requestHardRestart(currentHeader)
            }
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

        vpnJob = lifecycleScope.launch {
            try {
                // Now perfectly legal to call because this class IS an official VpnService
                val currentVpnInterface = establishVpnInterfaceReturn()
                if (currentVpnInterface != null) {
                    vpnInterface = currentVpnInterface
                    // one-shot pre-flight effort to ensure header
                    if (butler.prepareTunnelCredentials()) {
                        runVpnStack(currentVpnInterface.fd)
                        updateNotification(
                            "VPN Active",
                            R.drawable.ic_vpn_on,
                            AndroidColor.parseColor("#FFE4A7")
                        )
                        broadcastStatus("Connected", true)
                    } else {
                        closeInterfaceQuietly(currentVpnInterface)
                        vpnInterface = null
                    }
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

    override fun requestHardRestart(bootHeader: ByteArray) {
        Log.d(TS, "Executing atomic VPN tunnel interface cycle...")
        vpnJob?.cancel()
        closeInterfaceQuietly(vpnInterface)
        vpnInterface = null

        vpnJob = lifecycleScope.launch {
            val currentInterface = establishVpnInterfaceReturn()
            if (currentInterface != null) {
                vpnInterface = currentInterface
                runVpnStack(currentInterface.fd)
                broadcastStatus("Connected", true)
            }
        }
    }

    override fun onPreFlightAuthenticationFailed(reason: String) {
        handleConnectionFailure(reason)
    }

    private fun establishVpnInterfaceReturn(): ParcelFileDescriptor? {
        return try {
            // Evaluates perfectly against this instance context now!
            Builder()
                .setSession(getString(R.string.app_name))
                .addAddress(tunIp10_8_0_2, 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("223.5.5.5")
                .addDnsServer("223.6.6.6")
                .addDisallowedApplication(packageName) // Bypasses loopbacks securely
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
                    butler.activeSettings ?: throw IllegalStateException("VPN Settings not loaded.")
                val currentHeader = butler.header?: throw IllegalStateException("no valid header.")

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
        Log.d(TS, "Tearing down system VPN routing layouts...")
        butler.stopMonitoring()

        try { Troadengine.stopTroad() } catch (e: Exception) {}
        vpnJob?.cancel()

        lifecycleScope.launch(Dispatchers.IO) {
            closeInterfaceQuietly(vpnInterface)
            vpnInterface = null

            withContext(Dispatchers.Main) {
                broadcastStatus("Disconnected", false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun closeInterfaceQuietly(pfd: ParcelFileDescriptor?) {
        try { pfd?.close() } catch (e: IOException) { Log.e(TS, "FD close error", e) }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        butler.stopMonitoring()
        super.onDestroy()
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

}
