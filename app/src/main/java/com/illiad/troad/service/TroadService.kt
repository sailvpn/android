package com.illiad.troad.service

import FildesAddress
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
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
import com.illiad.troad.Consts.TAG
import com.illiad.troad.Consts.TUN_IP
import com.illiad.troad.model.TroadStore
import com.illiad.troad.MainActivity
import com.illiad.troad.R
import com.illiad.troad.service.Utils.fildesChannel
import com.illiad.troad.service.Utils.vpnInterface
import com.illiad.troad.service.channel.FildesChannel
import com.illiad.troad.service.codec.ip.PacketDecoder
import com.illiad.troad.service.handler.ip.DemuxHandler
import com.illiad.troad.service.security.Cryptos
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.util.concurrent.DefaultEventExecutorGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.FileDescriptor

/**
 * A [VpnService] that manages the VPN connection for the Troad application.
 *
 * This service is responsible for:
 * - Establishing and configuring the VPN tunnel.
 * - Handling the lifecycle of the VPN connection (start, stop).
 * - Managing a persistent notification to keep the service in the foreground.
 * - Reading and writing IP packets to the VPN interface using Netty.
 * - Broadcasting the VPN connection status to other parts of the app.
 */
class TroadService : VpnService() {

     // CoroutineScope for launching background tasks, using an IO dispatcher for network and file operations.
     // SupervisorJob for managing coroutines within the service, allowing child coroutines to fail without canceling the entire scope.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tStore by lazy { TroadStore(applicationContext) }
    private var observationJob: Job? = null

    /**
     * Called by the system when the service is first created.
     * Initializes the notification channel.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service Created.")
        createNotificationChannel()
        startObservingSettings()
    }

    /**
     * Called by the system every time a client starts the service using [startService].
     * Handles incoming intents to connect or disconnect the VPN.
     *
     * @param intent The Intent supplied to [startService], which contains the action to perform.
     * @param flags Additional data about this start request.
     * @param startId A unique integer representing this specific request to start.
     * @return The return value indicates what semantics the system should use for the service's current started state.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received: ${intent?.action}")
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (fildesChannel?.isActive == true) {
                    Log.d(TAG, "VPN already running.")
                    return START_STICKY
                }

                if (prepareVpn()) {
                    serviceScope.launch {
                        startVpn(vpnInterface?.fileDescriptor!!)
                    }
                    startForeground(NOTIFICATION_ID, createNotification("VPN Connected"))
                    Log.d(TAG, "VPN connection established.")
                } else {
                    Log.e(TAG, "Failed to establish VPN connection.")
                    stopVpn()

                }
            }

            ACTION_DISCONNECT -> {
                Log.d(TAG, "Disconnecting VPN.")
                stopVpn()
            }
        }
        // If the service is killed, restart it with the last intent (if connect was successful)
        // Or START_NOT_STICKY if you don't want it to auto-restart.
        return if (fildesChannel?.isActive == true) START_STICKY else START_NOT_STICKY
    }

    /**
     * Creates the notification channel required for Android 8.0 (API 26) and above.
     * This channel is used for the foreground service notification.
     */
    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    /**
     * Builds the persistent notification shown to the user while the VPN is active.
     * The notification provides status information and an action to disconnect the VPN.
     *
     * @param contentText The text to display in the notification body.
     * @return A configured [Notification] object.
     */
    private fun createNotification(contentText: String): Notification {
        // Intent to open the app when the notification is tapped
        val openAppIntent = Intent(
            this, MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingOpenAppIntent = PendingIntent.getActivity(
            this,
            PENDING_INTENT_REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent for the "Disconnect" action
        val disconnectIntent = Intent(this, TroadService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingDisconnectIntent = PendingIntent.getService(
            this,
            PENDING_INTENT_REQUEST_CODE_DISCONNECT,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.troy)
            .setContentTitle(getString(R.string.app_name) + " VPN")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingOpenAppIntent)
            .setOngoing(true)
            .addAction(
                R.drawable.cross,
                "Disconnect", pendingDisconnectIntent
            )
        // .setPublicVersion(publicNotification) // For lock screen visibility control (optional)
        // .setProgress(0, 0, true) // Indeterminate progress (optional, if connecting)

        // For Android 8.0 (API 26) and higher, channel ID is required.
        // It's set in createNotificationChannel() and used by the builder.

        return notificationBuilder.build()
    }


    /**
     * Prepares and establishes the VPN interface.
     * This method configures the VPN parameters like IP address, routes, DNS, and MTU using [VpnService.Builder].
     * It requests user permission if this is the first time the VPN is being established.
     *
     * @return `true` if the VPN interface was established successfully, `false` otherwise.
     */
    private fun prepareVpn(): Boolean {

        Log.d(TAG, "Preparing VPN interface at: $TUN_IP")
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(TUN_IP, 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(DNS1).addDnsServer(DNS2)
            .addDisallowedApplication(packageName)
            .setMtu(MTU)

        try {
            vpnInterface = builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "Error establishing VPN interface", e)
            sendBroadcast(
                Intent(ACTION_VPN_STATUS_BROADCAST).putExtra(
                    "status", "Error establishing VPN interface"
                )
            )
            return false
        }

        if (vpnInterface == null) {
            Log.e(TAG, "VPN establish returned null. User might have denied permission.")
            sendBroadcast(
                Intent(ACTION_VPN_STATUS_BROADCAST).putExtra(
                    "status", "PERMISSION_DENIED"
                )
            )
            return false
        }

        return true
    }

    /**
     * Initializes and starts the Netty pipeline to handle traffic from the VPN file descriptor.
     *
     * The pipeline consists of:
     * - [FildesChannel]: Reads raw IP packets from the VPN interface.
     * - [PacketDecoder]: Decodes the raw bytes into Pcap4j [Packet] objects.
     * - [DemuxHandler]: Processes the decoded packets and manages data forwarding.
     *
     * @param fd The [FileDescriptor] of the established VPN interface.
     */
    private fun startVpn(fd: FileDescriptor) {
        val ioGroup = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        val decoderGroup = DefaultEventExecutorGroup(1)
        val demuxGroup = DefaultEventExecutorGroup(3)

        val b = Bootstrap()
        fildesChannel = FildesChannel(null, fd)

        b.group(ioGroup)
            .channelFactory { fildesChannel }
            .handler(object : ChannelInitializer<FildesChannel>() {
                override fun initChannel(ch: FildesChannel) {
                    ch.pipeline()
                        .addLast(decoderGroup, PacketDecoder())
                        .addLast(demuxGroup, DemuxHandler)
                }
            })

        val fildesAddress = FildesAddress(fd)
        b.connect(fildesAddress, fildesAddress)
            .addListener { future ->
                if (future.isSuccess) {
                    Log.i(TAG, "VPN connection established.")
                    broadcastVpnStatus("Connected", fildesChannel?.isActive == true)
                } else {
                    Log.e(TAG, "VPN connection failed", future.cause())
                    broadcastVpnStatus("VPN connection failed", false)
                    stopVpn()
                }
            }
    }

    /**
     * Stops the VPN service, cleans up resources, and stops the foreground notification.
     * Call this when the VPN is meant to be fully shut down.
     */
    private fun stopVpn() {
        Log.i(TAG, "stopVpnService called")

        if (fildesChannel?.isActive == true) {
            fildesChannel?.close()?.sync()?.addListener { future ->
                {
                    if (!future.isSuccess) {
                        future.cause().printStackTrace()
                    }
                    Log.i(TAG, "VPN connection closed.")
                }
            }
        }
        vpnInterface?.close()
        vpnInterface = null
        Log.i(TAG, "VPN Service stopped")

        stopSelf()
    }

    /**
     * Broadcasts the VPN status (message and connection state).
     *
     * @param message A descriptive message about the current status (e.g., "Connecting...", "Connected", "Error").
     * @param connected True if the VPN is considered connected, false otherwise.
     */
    private fun broadcastVpnStatus(message: String, connected: Boolean) {
        val intent = Intent(ACTION_VPN_STATUS_BROADCAST).apply {
            putExtra(EXTRA_STATUS_MESSAGE, message)
            putExtra(EXTRA_IS_CONNECTED, connected)
        }
        sendBroadcast(intent)
        Log.d(TAG, "VPN status broadcast: '$message', Connected: $connected")

    }

    /**
     * Called by the system to notify a service that it is no longer used and is being removed.
     * Cleans up resources, stops the foreground notification, and broadcasts the disconnected status.
     */
    override fun onDestroy() {
        super.onDestroy()

        stopForeground(STOP_FOREGROUND_REMOVE)
        broadcastVpnStatus("Disconnected", false)
        Log.i(TAG, "VPN Service Destroyed.")
        serviceScope.cancel() // Stop all observations when service is killed
    }

    private fun startObservingSettings() {
        observationJob = serviceScope.launch {
            // Combine all flows into a single configuration stream
            combine(
                tStore.serverDomainFlow,
                tStore.serverPortFlow,
                tStore.caCertFlow,
                tStore.selectedCryptoFlow,
                tStore.sharedSecretFlow,
                tStore.jwtFlow
            ) { v ->
                // This data class acts as a snapshot of your current settings
                VpnSettings(
                    v[0] as String,
                    v[1] as Int,
                    v[2] as String,
                    v[3] as Cryptos,
                    v[4] as String,
                    v[5] as String
                )
            }.collectLatest { settings ->
                // This block runs whenever ANY of the 6 settings change
                Utils.settings = settings
                Log.d("TroadService", "Applying new config: ${settings.crypto.value} on ${settings.domain}")
            }
        }
    }

}
