package com.illiad.troad.service

import FildesAddress
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
import com.illiad.troad.Consts.TUN_IP
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
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import java.io.IOException

class TroadService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val tStore by lazy { TroadStore(applicationContext) }
    private var settingsObserver: Job? = null
    private var certManagerObserver: Job ? = null
    private var tokenManager: TokenManager? = null
    private var autoRenewObserver: Job? = null
    private var cryptoTypeObserver: Job? =null

    @Volatile
    private var vpnInterface: ParcelFileDescriptor? = null

    @Volatile
    private var vpnChannel: Channel? = null

    private var eventLoopGroup: NioEventLoopGroup? = null

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
            Log.d(TS, "Preparing VPN interface at: $TUN_IP")
            vpnInterface = Builder()
                .setSession(getString(R.string.app_name))
                .addAddress(TUN_IP, 24)
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
        eventLoopGroup = NioEventLoopGroup(2)

        val bootstrap = Bootstrap()
            .group(eventLoopGroup)
            // Replace NioSocketChannel with your custom FildesChannel if it wraps FileDescriptor
            .channelFactory { FildesChannel(null, vpnInterface!!.fileDescriptor) }
            .handler(object : ChannelInitializer<FildesChannel>() {
                override fun initChannel(ch: FildesChannel) {
                    ch.pipeline().addLast(PacketDecoder())
                    ch.pipeline().addLast(DemuxHandler)
                }
            })

        // Netty's connect/bind can be slow on emulators; await() keeps it in this coroutine
        val fildesAddress = FildesAddress(vpnInterface!!.fileDescriptor)
        val future = bootstrap.connect(fildesAddress, fildesAddress).await()
        if (future.isSuccess) {
            vpnChannel = future.channel()
            Log.d(TS, "Netty pipeline established.")
        } else {
            throw IOException("Netty failed to bind to TUN", future.cause())
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
            combine(
                tStore.serverDomainFlow,
                tStore.serverPortFlow,
                tStore.caCertFlow,
                tStore.selectedCryptoFlow,
                tStore.sharedSecretFlow,
                tStore.jwtFlow,
                tStore.usernameFlow,
                tStore.passwordFlow,
                tStore.durationFlow,
                tStore.autoRenewFlow
            ) { v ->
                // This data class acts as a snapshot of your current settings
                Settings(
                    v[0] as String,
                    v[1] as Int,
                    v[2] as String,
                    v[3] as Cryptos,
                    v[4] as String,
                    v[5] as String,
                    v[6] as String,
                    v[7] as String,
                    v[8] as Duration,
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
}
