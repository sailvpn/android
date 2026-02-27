package com.illiad.troad.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.illiad.troad.Consts.ACTION_CONNECT
import com.illiad.troad.Consts.ACTION_DISCONNECT
import com.illiad.troad.Consts.ACTION_VPN_STATUS_BROADCAST
import com.illiad.troad.Consts.DNS1
import com.illiad.troad.Consts.DNS2
import com.illiad.troad.Consts.EXTRA_IS_CONNECTED
import com.illiad.troad.Consts.EXTRA_STATUS_MESSAGE
import com.illiad.troad.Consts.MTU
import com.illiad.troad.Consts.NOTIFICATION_CHANNEL_ID
import com.illiad.troad.Consts.NOTIFICATION_CHANNEL_NAME
import com.illiad.troad.Consts.NOTIFICATION_ID
import com.illiad.troad.Consts.PENDING_INTENT_REQUEST_CODE_DISCONNECT
import com.illiad.troad.Consts.PENDING_INTENT_REQUEST_CODE_OPEN_APP
import com.illiad.troad.Consts.TS
import com.illiad.troad.MainActivity
import com.illiad.troad.R
import com.illiad.troad.Settings
import com.illiad.troad.Utils
import com.illiad.troad.model.Duration
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.channel.FildesChannel
import com.illiad.troad.service.codec.ip.PacketDecoder
import com.illiad.troad.service.handler.ip.DemuxHandler
import com.illiad.troad.service.security.CertManager
import com.illiad.troad.service.security.Cryptos
import com.illiad.troad.service.security.client.TokenManager
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

@SuppressLint("VpnServicePolicy")
class TroadService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val tStore by lazy { TroadStore(applicationContext) }
    private var settingsObserver: Job? = null
    private var certManagerObserver: Job? = null
    private var tokenManager: TokenManager? = null
    private var autoRenewObserver: Job? = null
    private var cryptoTypeObserver: Job? = null

    @Volatile
    private var vpnInterface: ParcelFileDescriptor? = null

    @Volatile
    private var vpnChannel: Channel? = null

    private var eventLoopGroup: MultiThreadIoEventLoopGroup? = null

    override fun onCreate() {
        super.onCreate()
        observeSettings()
        observeCertManager()
        tokenManager = TokenManager.getInstance(applicationContext)
        observeAutorenew()
        observeCryptoType()
        Log.d(TS, "VPN Service Created.")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TS, "onStartCommand received: $action")

        when (action) {
            ACTION_CONNECT -> handleConnect()
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_STICKY
    }

    private fun handleConnect() {
        if (vpnInterface != null) {
            Log.d(TS, "VPN already running.")
            return
        }

        // 1. IMMEDIATELY start foreground to prevent ANR/System Kill
        startForeground(NOTIFICATION_ID, createNotification("Connecting..."))

        // 2. Offload heavy Netty/Vpn setup to IO thread
        serviceScope.launch {
            try {
                if (establishVpnInterface()) {
                    startNettyStack()
                    updateNotification("VPN Connected and Active")
                    broadcastStatus("Connected", true)
                } else {
                    Log.e(TS, "Failed to establish VPN interface.")
                    stopVpn()
                }
            } catch (e: Exception) {
                Log.e(TS, "Critical error during VPN startup", e)
                stopVpn()
            }
        }
    }

    private fun establishVpnInterface(): Boolean {
        return try {
            val currentPrefixes = getActiveNetworkPrefixes()
            // Logic: If any active network uses 10.x.x.x, move the VPN to 172.19.x.x
            val tunIp = if (currentPrefixes.any { it.startsWith("10.") }) {
                "172.19.0.1"
            } else {
                "10.8.0.2"
            }
            Log.d(TS, "Preparing VPN interface at: $tunIp")
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
            Log.e(TS, "VpnService.Builder failed", e)
            false
        }
    }

    private suspend fun startNettyStack() = withContext(Dispatchers.IO) {
        // Use a fixed thread count (2) to avoid Netty trying to read 'somaxconn' (SELinux fix)
        eventLoopGroup = MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory())

        // 1. Create the channel instance manually
        val channel = FildesChannel(null, vpnInterface!!.fileDescriptor)
        channel.pipeline().addLast(PacketDecoder())
        channel.pipeline().addLast(DemuxHandler)
        // Final catch for errors in the pipeline
        channel.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                Log.e(TS, "Netty Pipeline Error: ${cause.message}")
                // Do NOT rethrow. This keeps the app alive.
            }
        })

        // 2. Register it to your EventLoopGroup
        val registerFuture = eventLoopGroup!!.next()
            .register(channel)

        registerFuture.addListener { future ->
            if (future.isSuccess) {
                // This ensures the pipeline knows the "cable is plugged in"
                channel.pipeline().fireChannelActive()

                // This triggers the first call to doBeginRead()
                channel.pipeline().read()

                Log.d(TS, "FildesChannel registered and active")
            } else {
                Log.e(TS, "Failed to register FildesChannel", future.cause())
            }
        }

    }

    private fun stopVpn() {
        Log.d(TS, "Stopping VPN Service...")
        broadcastStatus("Disconnected", false)

        serviceScope.launch {
            vpnChannel?.close()?.await()
            eventLoopGroup?.shutdownGracefully()

            withContext(Dispatchers.Main) {
                try {
                    vpnInterface?.close()
                } catch (e: IOException) {
                    Log.e(TS, "Error closing VPN interface", e)
                }
                vpnInterface = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun broadcastStatus(message: String, isConnected: Boolean) {
        val intent = Intent(ACTION_VPN_STATUS_BROADCAST).apply {
            putExtra(EXTRA_STATUS_MESSAGE, message)
            putExtra(EXTRA_IS_CONNECTED, isConnected)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, PENDING_INTENT_REQUEST_CODE_OPEN_APP,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = PendingIntent.getService(
            this, PENDING_INTENT_REQUEST_CODE_DISCONNECT,
            Intent(this, TroadService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.troy)
            .setContentTitle("${getString(R.string.app_name)} VPN")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.cross, "Disconnect", disconnectIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW // Low priority avoids annoying sounds on every update
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        settingsObserver?.cancel()
        certManagerObserver?.cancel()
        autoRenewObserver?.cancel()
        cryptoTypeObserver?.cancel()
        super.onDestroy()
    }

    private fun observeSettings() {
        settingsObserver?.cancel()
        settingsObserver = serviceScope.launch {
            // Combine all flows into a single configuration stream
            combine<Any, Settings>(
                tStore.serverDomainFlow,
                tStore.serverPortFlow,
                tStore.caCertFlow,
                tStore.selectedCryptoFlow,
                tStore.sharedSecretFlow,
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
                    secret = v[4] as String,
                    jwt = v[5] as String,
                    username = v[6] as String,
                    password = v[7] as String,
                    duration = v[8] as Duration
                )
            }.collectLatest { settings ->
                // This block runs whenever ANY of the 6 settings change
                Utils.settings = settings
            }
        }
    }

    private fun observeCertManager() {
        certManagerObserver?.cancel()
        certManagerObserver = serviceScope.launch {
            tStore.caCertFlow
                .filter { cert -> cert.isNotEmpty() }
                .collectLatest { cert ->
                    CertManager.updateContext(cert)
                }
        }
    }

    private fun observeAutorenew() {
        autoRenewObserver?.cancel()
        autoRenewObserver = serviceScope.launch {
            tStore.autoRenewFlow
                .collectLatest { autoRenew ->
                    tokenManager?.manageRenew(autoRenew.minutes)
                }
        }
    }

    private fun observeCryptoType() {
        cryptoTypeObserver?.cancel()
        cryptoTypeObserver = serviceScope.launch {
            tStore.selectedCryptoFlow
                .filter { crypt ->
                    Cryptos.JWT != crypt
                }
                .collectLatest { crypt ->
                    tokenManager?.manageRenew(0L)
                }

        }
    }

    fun getActiveNetworkPrefixes(): List<String> {
        val prefixes = mutableListOf<String>()
        try {
            // Get all interfaces on the device (wlan0, rmnet0, etc.)
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()

            for (networkInterface in Collections.list(interfaces)) {
                // Only check interfaces that are currently active and NOT the loopback (localhost)
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                val addresses = networkInterface.inetAddresses
                for (address in Collections.list(addresses)) {
                    // We only care about IPv4 for standard TUN range detection
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress
                        // Extract prefix (e.g., "192.168.1.15" -> "192.168.1")
                        val prefix = ip!!.substringBeforeLast(".")
                        prefixes.add(prefix)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return prefixes.distinct() // Remove duplicates if an interface has multiple IPs
    }
}
