package com.illiad.troad.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.illiad.troad.Consts.ACTION_CONNECT
import com.illiad.troad.Consts.ACTION_DISCONNECT
import com.illiad.troad.Consts.ACTION_RESTART
import com.illiad.troad.Consts.ACTION_VPN_STATUS_BROADCAST
import com.illiad.troad.Consts.EXTRA_MSG
import com.illiad.troad.Consts.EXTRA_STATE
import com.illiad.troad.Consts.MTU
import com.illiad.troad.Consts.CHANNEL_ID
import com.illiad.troad.Consts.CHANNEL_NAME
import com.illiad.troad.Consts.NOTIFICATION_ID
import com.illiad.troad.Consts.DNS1111
import com.illiad.troad.Consts.DNS1001
import com.illiad.troad.Consts.TS
import com.illiad.troad.Consts.tunIp10_8_0_2
import com.illiad.troad.MainActivity
import com.illiad.troad.R
import com.illiad.troad.model.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import troadengine.Troadengine
import java.io.File
import java.io.IOException
import androidx.core.graphics.toColorInt
import com.illiad.troad.Consts.DNS8888
import com.illiad.troad.Consts.DNS9999
import com.illiad.troad.Consts.INTENT_DISCONNECT
import com.illiad.troad.Consts.INTENT_OPEN_APP
import com.illiad.troad.service.security.CaCert.getCertFile

// 1. CHANGE INHERITANCE: Must be VpnService, manually providing LifecycleOwner
@SuppressLint("VpnServicePolicy")
class TroadService : VpnService(), LifecycleOwner, Butler.TunnelInterfaceController {

    // 2. MANUALLY IMPLEMENT LIFECYCLE CONTAINER: Provides 'lifecycleScope' safely
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val butler by lazy { Butler.getInstance(applicationContext) }
    private var vpnJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()

        // 1. CONSTRUCT THE MANDATORY NOTIFICATION CHANNEL CONTAINER:
        val importance =
            NotificationManager.IMPORTANCE_LOW // Keeps it quiet, preventing notification sounds

        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
            description = "Maintains active device encryption traffic monitoring"
            setShowBadge(false)
        }

        // Register the channel with the Android OS notification subsystem
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
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

        // Clear and explicit parsing avoids color integer definition faults
        val notification = createNotification(
            "Connecting...",
            R.drawable.ic_vpn_on,
            "#0284C7".toColorInt()
        )

        broadcastStatus(VpnState.CONNECTING, "Connecting...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                // CRITICAL SYNC FIX: Matches foregroundServiceType="systemExempted" inside the Manifest!
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 4. FIRE COROUTINE PRE-FLIGHT VALIDATION:
        vpnJob = lifecycleScope.launch {
            try {
                val currentInterface = establishVpnInterfaceReturn()
                if (currentInterface != null) {
                    vpnInterface = currentInterface

                    // Call your unified Butler one-shot pre-flight authentication verification blocker
                    broadcastStatus(VpnState.CONNECTING, "Authenticating...")

                    if (butler.prepareHeader()) {

                        // Launch the native blocking Go loop thread safely
                        runVpnStack(currentInterface.fd)

                        // Update layout color parameters to your Sand-Gold highlight theme
                        updateNotification(
                            "Sail ON", R.drawable.ic_vpn_on,
                            "#FFE4A7".toColorInt()
                        )
                        broadcastStatus(VpnState.CONNECTED)
                    } else {
                        // Pre-flight authorization rejected or network timed out
                        handleConnectionFailure("Authentication failed. Please check your credentials.")
                    }
                } else {
                    handleConnectionFailure("Failed to allocate secure system routing interface.")
                }
            } catch (e: Exception) {
                Log.e(TS, "Fatal Internal Error inside startup pipeline", e)
                handleConnectionFailure("Internal software error occurred during connection configuration.")
            }
        }
    }

    override fun requestHardRestart(bootHeader: ByteArray) {
        Log.d(TS, "Executing atomic VPN tunnel interface cycle...")
        vpnJob?.cancel()

        // Ensure the native engine is stopped before cycling the interface
        try {
            Troadengine.stopTroad()
        } catch (_: Exception) {}

        closeInterfaceQuietly(vpnInterface)
        vpnInterface = null

        vpnJob = lifecycleScope.launch {
            val currentInterface = establishVpnInterfaceReturn()
            if (currentInterface != null) {
                vpnInterface = currentInterface
                runVpnStack(currentInterface.fd)
                broadcastStatus(VpnState.CONNECTED, "Connected")
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
                .addDnsServer(DNS1001)
                .addDnsServer(DNS1111)
                .addDnsServer(DNS8888)
                .addDnsServer(DNS9999)
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
                val currentHeader = butler.header ?: throw IllegalStateException("no valid header.")

                Log.i(TS, "Go Engine Thread Started")

                // blocks here until stopped or network pipe disconnects
                Troadengine.startTroad(
                    fd.toLong(),
                    currentSettings.domain + ":" + currentSettings.port.toString(),
                    currentHeader,
                    getCertFile(applicationContext)!!.absolutePath,
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

        try {
            Troadengine.stopTroad()
        } catch (_: Exception) {
        }
        vpnJob?.cancel()

        lifecycleScope.launch(Dispatchers.IO) {
            closeInterfaceQuietly(vpnInterface)
            vpnInterface = null

            withContext(Dispatchers.Main) {
                broadcastStatus(VpnState.DISCONNECTED, "Disconnected")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun closeInterfaceQuietly(pfd: ParcelFileDescriptor?) {
        try {
            pfd?.close()
        } catch (e: IOException) {
            Log.e(TS, "FD close error", e)
        }
    }

    override fun onRevoke() {
        Log.w(TS, "VPN permission revoked by system.")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        butler.stopMonitoring()
        super.onDestroy()
    }

    private fun broadcastStatus(state: VpnState, msg: String? = null) {
        val intent = Intent(ACTION_VPN_STATUS_BROADCAST).apply {
            // Safe primitive string transmission prevents serialization crashes
            putExtra(EXTRA_STATE, state.name)
            if (msg != null) {
                putExtra(EXTRA_MSG, msg)
            }
        }
        sendBroadcast(intent)
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

        val buildPendingIntent = { action: String, code: Int ->
            val isDisconnect = action == ACTION_DISCONNECT
            val intent = Intent(
                this,
                if (isDisconnect) TroadService::class.java else MainActivity::class.java
            ).apply {
                this.action = action
                if (!isDisconnect) {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }

            if (isDisconnect) {
                PendingIntent.getService(
                    this, code, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getActivity(
                    this, code, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        }

        // Use NotificationCompat.Builder to abstract away underlying version quirks safely
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(iconResId) // CRITICAL: Ensure R.drawable.ic_vpn_on exists and compiles cleanly
            .setColor(statusColor)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(buildPendingIntent("MainView", INTENT_OPEN_APP))
            .addAction(
                iconResId, // Use your existing valid icon asset identifier for the disconnect button row
                "Disconnect",
                buildPendingIntent(ACTION_DISCONNECT, INTENT_DISCONNECT)
            )
            .build()
    }


    /**
     * CONNECTION FAIL HANDLER: Holds the app process alive in memory space,
     * resets UI layouts safely to Disconnected states, and alerts the client.
     */

    private fun handleConnectionFailure(reason: String) {
        Log.w(TS, "VPN Connection Aborted: $reason")

        updateNotification(
            "Connection Failed. Tap to reconnect.",
            R.drawable.ic_vpn_off,
            "#F1F5F9".toColorInt()
        )

        // BROADCAST UP TO UI: Pass the exact error message string along with the failed state
        broadcastStatus(VpnState.ERROR, reason)

        vpnJob?.cancel()

        lifecycleScope.launch(Dispatchers.IO) {
            closeInterfaceQuietly(vpnInterface)
            vpnInterface = null

            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    /**
     * SOFTWARE FAIL HANDLER: Safely shuts down the service due to internal errors (like missing certs),
     * without killing the whole application process.
     */
    private fun handleSoftwareFailure(errorMessage: String) {
        Log.e(TS, "Software Failure: $errorMessage")

        // 1. Broadcast the error to UI
        broadcastStatus(VpnState.ERROR, errorMessage)

        // 2. Clean up local tunnel resources
        vpnJob?.cancel()
        try {
            vpnInterface?.close()
        } catch (_: Exception) { /* no-op */
        }
        vpnInterface = null

        // 3. Update notification to error state
        updateNotification(
            "Service Error: Tap to check settings.",
            R.drawable.ic_vpn_off,
            "#F1F5F9".toColorInt()
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
