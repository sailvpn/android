package com.illiad.troad.service

import FildesAddress
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.illiad.troad.Consts.EXTRA_SERVER_ADDRESS
import com.illiad.troad.Consts.EXTRA_SERVER_PORT
import com.illiad.troad.Consts.EXTRA_SHARED_SECRET
import com.illiad.troad.Consts.EXTRA_STATUS_MESSAGE
import com.illiad.troad.Consts.MTU
import com.illiad.troad.Consts.NOTIFICATION_CHANNEL_ID
import com.illiad.troad.Consts.NOTIFICATION_CHANNEL_NAME
import com.illiad.troad.Consts.NOTIFICATION_ID
import com.illiad.troad.Consts.PENDING_INTENT_REQUEST_CODE_DISCONNECT
import com.illiad.troad.Consts.PENDING_INTENT_REQUEST_CODE_OPEN_APP
import com.illiad.troad.Consts.TAG
import com.illiad.troad.Consts.TUN_IP
import com.illiad.troad.MainActivity
import com.illiad.troad.R
import com.illiad.troad.service.Utils.serverDomain
import com.illiad.troad.service.Utils.serverPort
import com.illiad.troad.service.Utils.sharedSecret
import com.illiad.troad.service.channel.FildesChannel
import com.illiad.troad.service.codec.ip.PacketDecoder
import com.illiad.troad.service.handler.ip.DemuxHandler
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFactory
import io.netty.channel.ChannelInitializer
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.FileDescriptor

class TroadService : VpnService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var fildesChannel: FildesChannel? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service Created.")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received: ${intent?.action}")
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (fildesChannel?.isActive == true) {
                    Log.d(TAG, "VPN already running.")
                    // Optionally update notification or parameters if needed
                    return START_STICKY
                }
                // Retrieve parameters from the intent (if you pass them this way)
                serverDomain = intent.getStringExtra(EXTRA_SERVER_ADDRESS)!!
                serverPort = intent.getIntExtra(EXTRA_SERVER_PORT, 0)
                sharedSecret = intent.getStringExtra(EXTRA_SHARED_SECRET)!!


                // Prepare the VPN connection
                if (prepareVpn()) {
                    serviceScope.launch {
                        startVpn(vpnInterface?.fileDescriptor!!)
                    }
                    startForeground(NOTIFICATION_ID, createNotification("VPN Connected"))
                    Log.d(TAG, "VPN connection established.")
                } else {
                    Log.e(TAG, "Failed to establish VPN connection.")
                    serviceScope.launch {
                        stopVpn() // Clean up and stop
                    }
                }
            }

            ACTION_DISCONNECT -> {
                Log.d(TAG, "Disconnecting VPN.")
                serviceScope.launch {
                    stopVpn()
                }
            }
        }
        // If the service is killed, restart it with the last intent (if connect was successful)
        // Or START_NOT_STICKY if you don't want it to auto-restart.
        return if (fildesChannel?.isActive == true) START_STICKY else START_NOT_STICKY
    }

    private fun createNotificationChannel() { // Definition of your method
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel) // Good to add null check for manager
    }

    private fun createNotification(contentText: String): Notification {
        // Intent to open the app when the notification is tapped
        val openAppIntent = Intent(
            this, MainActivity::class.java
        ).apply { // Replace MainActivity with your main activity
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
            .setSmallIcon(R.drawable.troy) // Replace with your VPN icon
            .setContentTitle(getString(R.string.app_name) + " VPN") // App name + " VPN"
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Or PRIORITY_LOW for less intrusive
            .setContentIntent(pendingOpenAppIntent) // Action on tap
            .setOngoing(true) // Makes the notification non-dismissable by swiping
            .addAction(
                R.drawable.cross, // Replace with your disconnect icon (optional)
                "Disconnect", pendingDisconnectIntent
            )
        // .setPublicVersion(publicNotification) // For lock screen visibility control (optional)
        // .setProgress(0, 0, true) // Indeterminate progress (optional, if connecting)

        // For Android 8.0 (API 26) and higher, channel ID is required.
        // It's set in createNotificationChannel() and used by the builder.

        return notificationBuilder.build()
    }


    private fun prepareVpn(): Boolean {

        Log.d(TAG, "Preparing VPN interface at: $TUN_IP")
        // --- This is a crucial part where you configure the VPN ---
        val builder = Builder()
            // Configure IP address, routes, DNS servers, MTU, etc.
            // These are examples and MUST be configured according to your VPN server setup.
            .setSession(getString(R.string.app_name)) // Display name for the VPN session
            .addAddress(TUN_IP, 24)      // VPN client's virtual IP
            .addRoute("0.0.0.0", 0)          // Route all traffic through the VPN
            .addDnsServer(DNS1).addDnsServer(DNS2) // Set MTU (adjust as needed)
            //  .addAllowedApplication("com.example.anotherapp") // For per-app VPN (optional)
            .addDisallowedApplication(packageName)           // Exclude this app (optional)
            .setMtu(MTU)
        // Optional: Configure an intent to open your app's settings if needed before connection
        // val configureIntent = Intent(this, YourVpnSettingsActivity::class.java)
        // builder.setConfigureIntent(PendingIntent.getActivity(this, 0, configureIntent, PendingIntent.FLAG_IMMUTABLE))

        try {
            vpnInterface = builder.establish() // This can return null if user denies permission
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

    private fun startVpn(fd: FileDescriptor) {

        // Configure the bootstrap.
        val group = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())
        val b = Bootstrap()
        fildesChannel = FildesChannel(null, fd)
        b.group(group).channelFactory(ChannelFactory { fildesChannel })
            .handler(object : ChannelInitializer<FildesChannel>() {
                override fun initChannel(ch: FildesChannel?) {
                    ch!!.pipeline()
                        .addLast(PacketDecoder())
                        .addLast(DemuxHandler)
                }
            })
        val fildesAddress = FildesAddress(fd)
        b.connect(fildesAddress, fildesAddress)
            .addListener { future ->
                {
                    if (future.isSuccess) {
                        Log.i(TAG, "VPN connection established.")
                        broadcastVpnStatus("Connected", fildesChannel?.isActive == true)
                    } else {
                        sendBroadcast(
                            Intent(ACTION_VPN_STATUS_BROADCAST).putExtra(
                                "status", "VPN connection failed"
                            )
                        )
                        future.cause().printStackTrace()
                    }
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
        Log.i(TAG, "VPN Service stopped")
        broadcastVpnStatus("Disconnected", false) // Notify UI
        stopSelf() // Stop the service itself
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
            // Optional: If this broadcast is only meant for components within your app,
            // you can make it more secure and efficient by setting the package.
            // This prevents other apps from intercepting it.
            // setPackage(packageName)
        }
        sendBroadcast(intent) // Sends a system-wide broadcast
        Log.d(TAG, "VPN status broadcast: '$message', Connected: $connected")

    }

    override fun onDestroy() {
        super.onDestroy()

        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "VPN Service Destroyed.")
        // Ensure all resources are cleaned up if not already done.
        // This is a final safeguard.
    }

}