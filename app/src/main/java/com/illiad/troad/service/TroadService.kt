package com.illiad.troad.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.illiad.troad.Consts.ACTION_VPN_STATUS_BROADCAST
import com.illiad.troad.Consts.ACTION_CONNECT
import com.illiad.troad.Consts.ACTION_DISCONNECT
import com.illiad.troad.Consts.DNS1
import com.illiad.troad.Consts.DNS2
import com.illiad.troad.Consts.EXTRA_IS_CONNECTED
import com.illiad.troad.Consts.EXTRA_STATUS_MESSAGE
import com.illiad.troad.Consts.MTU
import com.illiad.troad.Consts.NOTIFICATION_CHANNEL_ID
import com.illiad.troad.Consts.NOTIFICATION_CHANNEL_NAME
import com.illiad.troad.Consts.NOTIFICATION_ID
import com.illiad.troad.Consts.PENDING_INTENT_REQUEST_CODE_DISCONNECT
import com.illiad.troad.Consts.TS
import com.illiad.troad.MainActivity
import com.illiad.troad.R
import com.illiad.troad.Utils
import com.illiad.troad.model.Duration
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.security.Cryptos
import com.illiad.troad.service.security.HeaderEncoder
import com.illiad.troad.service.security.client.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import troadengine.Troadengine

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

        startForeground(NOTIFICATION_ID, createNotification("Connecting..."))

        vpnJob = serviceScope.launch {
            try {
                if (establishVpnInterface()) {
                    runVpnStack(vpnInterface!!.fd) // Logic for tun2socks / native engine goes here
                    updateNotification("VPN Active")
                    broadcastStatus("Connected", true)
                } else {
                    stopVpn()
                }
            } catch (e: Exception) {
                Log.e(TS, "Fatal VPN error", e)
                stopVpn()
            }
        }
    }

    private fun establishVpnInterface(): Boolean {
        return try {
            // Native Kotlin logic to determine IP without NetworkInterface.getNetworkInterfaces()
            val tunIp = "10.8.0.2"

            vpnInterface = Builder()
                .setSession(getString(R.string.app_name))
                .addAddress(tunIp, 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(DNS1)
                .addDnsServer(DNS2)
                .addDisallowedApplication(packageName)
                .setMtu(MTU)
                .establish()

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
    private suspend fun runVpnStack(fd: Int) = withContext(Dispatchers.IO) {
        val settings = Utils.settings ?: return@withContext

        Log.i(TS, "Starting tun2socks engine on FD: $fd")


        // 1. Start the native engine.
        // If your Go implementation is blocking, this call won't return until stopped.
        //	StartTroad(fd, "proxy.example.com:443", "my-token", "/path/to/ca.pem", "myserver.com", 1300)
        val result = Troadengine.startTroad(
            fd,
            settings.domain ?: "127.0.0.1",
            settings.port ?: 5001,
            MTU,
            caCert = settings.cacert,
            header = Utils.header!!,
            sni = ""
        )

        if (result != null) {
            Log.e(TS, "Native engine failed to start with code: $result")
            throw RuntimeException("tun2socks startup failure")
        }

        // 2. Keep the coroutine alive and monitor for cancellation
        try {
            while (isActive) {
                // Check if the interface is still valid
                if (vpnInterface == null) break
                delay(1000)
            }
        } finally {
            // 3. Ensure the engine stops if the coroutine is cancelled (e.g., stopVpn() called)
            withContext(NonCancellable) {
                Log.i(TS, "Shutting down native tun2socks engine")
                Troadengine.stopTroad()
            }
        }
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

    private fun observeSettings() {

        serviceScope.launch {
            // Combine all flows into a single configuration stream
            combine<Any, Settings>(
                tStore.serverDomainFlow,
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
                    port = v[1] as Int,
                    cacert = v[2] as String,
                    crypto = v[3] as Cryptos,
                    jwt = v[5] as String,
                    username = v[6] as String,
                    password = v[7] as String,
                    duration = v[8] as Duration
                )
            }.collectLatest { s ->
                // This block runs whenever ANY of the 6 settings change
                Utils.settings = s
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

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = { action: String, code: Int ->
            val intent = Intent(
                this,
                if (action == ACTION_DISCONNECT) TroadService::class.java else MainActivity::class.java
            )
            intent.action = action
            PendingIntent.getService(this, code, intent, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.troy)
            .setContentTitle("Troad VPN")
            .setContentText(text)
            .setOngoing(true)
            .addAction(
                R.drawable.cross,
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
        serviceScope.cancel()
        super.onDestroy()
    }
}
