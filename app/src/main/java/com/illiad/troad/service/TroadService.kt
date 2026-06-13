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
import com.illiad.troad.Consts.DNS1001
import com.illiad.troad.Consts.DNS1111
import com.illiad.troad.Consts.DNS8888
import com.illiad.troad.Consts.DNS9999
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
import com.illiad.troad.Utils
import com.illiad.troad.Utils.settings
import com.illiad.troad.model.Duration
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.security.Cryptos
import com.illiad.troad.service.security.HeaderEncoder
import com.illiad.troad.service.security.client.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import troadengine.Troadengine
import java.io.File

@SuppressLint("VpnServicePolicy")
class TroadService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tStore by lazy { TroadStore(applicationContext) }
    private lateinit var tokenManager: TokenManager

    @Volatile
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager.getInstance(applicationContext)
        createNotificationChannel()
        observeSettings()
        observeAutorenew()
        maintainHeadr()
        Log.d(TS, "VPN Service Created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> handleConnect()
            ACTION_DISCONNECT -> stopVpn()
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
                if (establishVpnInterface()) {
                    runVpnStack(vpnInterface!!.fd)
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

    private fun establishVpnInterface(): Boolean {
        return try {
            vpnInterface = Builder()
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
            Log.i(TS, "Establishing VPN Interface")
            vpnInterface != null
        } catch (e: Exception) {
            Log.e(TS, "Vpn Builder failed", e)
            false
        }
    }


    /**
     * Executes the native tun2socks engine.
     * This function suspends until the VPN is stopped or the coroutine is cancelled.
     */

    private fun runVpnStack(fd: Int) {
        Thread({
            try {
                // SOFTWARE FAIL CHECK: Validate file system write health immediately
                val certFile = File(applicationContext.cacheDir, "proxy_ca.crt")
                if (settings?.cacert.isNullOrEmpty()) {
                    throw IllegalStateException("Missing necessary security CA Certificates.")
                }
                certFile.writeText(settings!!.cacert)

                Log.i(TS, "Go Engine Thread Started")

                // blocks here until stopped or network pipe disconnects
                Troadengine.startTroad(
                    fd.toLong(),
                    settings!!.domain + ":" + settings!!.port.toString(),
                    Utils.header!!,
                    certFile.absolutePath,
                    settings!!.sni,
                    MTU.toLong()
                )

                Log.i(TS, "Go Engine Thread Exited Normally")
            } catch (e: IllegalStateException) {
                // SOFTWARE FAIL: Missing assets or native library linking failures
                Log.e(TS, "Software Setup Aborted: ${e.message}")
                Handler(Looper.getMainLooper()).post { terminateEntireAppSilently() }
            } catch (e: Exception) {
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
     * SOFTWARE FAIL HANDLER: Destroys foreground constraints entirely and
     * shuts down background execution tasks due to corrupted environments.
     */
    private fun terminateEntireAppSilently() {
        broadcastStatus("Internal Application Error", false)
        vpnJob?.cancel()
        try {
            vpnInterface?.close()
        } catch (e: Exception) { /* no-op */
        }
        vpnInterface = null

        // Kill the foreground notification and exit out of the background loop entirely
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun observeSettings() {

        serviceScope.launch {
            // Combine all flows into a single configuration stream
            combine<Any, Settings>(
                tStore.serverDomainFlow,
                tStore.sniFlow,
                tStore.serverPortFlow,
                tStore.caCertFlow,
                tStore.selectedCryptoFlow,
                tStore.tunIpFlow,
                tStore.jwtFlow,
                tStore.usernameFlow,
                tStore.passwordFlow,
                tStore.durationFlow
            ) { v ->
                // This data class acts as a snapshot of your current settings
                Settings(
                    domain = v[0] as String,
                    sni = v[1] as String,
                    port = v[2] as Int,
                    cacert = v[3] as String,
                    crypto = v[4] as Cryptos,
                    jwt = v[5] as String,
                    username = v[6] as String,
                    password = v[7] as String,
                    duration = Duration.fromLabel(v[8] as String)
                )
            }.collectLatest { s ->
                // This block runs whenever ANY of the 6 settings change
                settings = s
            }
        }
    }

    private fun observeAutorenew() {
        serviceScope.launch {
            tStore.autoRenewFlow.collectLatest { renew ->
                tokenManager.manageRenew(renew.minutes)
            }

        }
    }

    // crate a new headr whenever crypt, sharesecret, or jwt changes
    private fun maintainHeadr() {
        serviceScope.launch {
            combine<Any, String>(
                tStore.selectedCryptoFlow,
                tStore.sharedSecretFlow,
                tStore.jwtFlow
            ) { v ->
                HeaderEncoder.encodeHeader(
                    v.get(0) as Cryptos,
                    v.get(1) as String,
                    v.get(2) as String
                )

            }.collectLatest { header ->
                // This block runs whenever ANY of the 6 settings change
                Utils.header = header
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
        Log.w(TS, "VPN Service is being permanently destroyed by the system wrapper. Cleaning resources...")

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
