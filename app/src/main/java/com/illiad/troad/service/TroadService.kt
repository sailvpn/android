package com.illiad.troad.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.illiad.troad.Consts
import com.illiad.troad.MainActivity
import com.illiad.troad.R
import com.illiad.troad.service.Utils.vpnInterface
import com.illiad.troad.service.Utils.vpnReadFileChannel
import com.illiad.troad.service.Utils.vpnWriteFileChannel
import com.illiad.troad.service.Utils.vpnReaderExecutor
import com.illiad.troad.service.Utils.isRunning
import com.illiad.troad.service.codec.ip.PacketDecoder
import com.illiad.troad.service.handler.ip.DemuxHandler
import com.illiad.troad.service.handler.ip.InputHandler
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit.MILLISECONDS

class TroadService : VpnService() {

    // To pass parameters from your UI to the service (optional)
    private var serverAddress: String? = null
    private var serverPort: Int = 0
    private var sharedSecret: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(Consts.TAG, "VPN Service Created.")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(Consts.TAG, "onStartCommand received: ${intent?.action}")
        when (intent?.action) {
            Consts.ACTION_CONNECT -> {
                if (Utils.isRunning) {
                    Log.d(Consts.TAG, "VPN already running.")
                    // Optionally update notification or parameters if needed
                    return START_STICKY
                }
                // Retrieve parameters from the intent (if you pass them this way)
                serverAddress = intent.getStringExtra(Consts.EXTRA_SERVER_ADDRESS)
                serverPort = intent.getIntExtra(Consts.EXTRA_SERVER_PORT, 0)
                sharedSecret = intent.getStringExtra(Consts.EXTRA_SHARED_SECRET)

                Log.d(Consts.TAG, "Connecting VPN to $serverAddress:$serverPort")

                // Prepare and establish the VPN connection
                if (prepareAndEstablishVpn()) {
                    Utils.isRunning = true
                    startForeground(Consts.NOTIFICATION_ID, createNotification("VPN Connected"))

                    Log.d(Consts.TAG, "VPN connection established and foreground service started.")
                } else {
                    Log.e(Consts.TAG, "Failed to establish VPN connection.")
                    stopVpnService() // Clean up and stop
                }
            }

            Consts.ACTION_DISCONNECT -> {
                Log.d(Consts.TAG, "Disconnecting VPN.")
                disconnectVpn()
            }
        }
        // If the service is killed, restart it with the last intent (if connect was successful)
        // Or START_NOT_STICKY if you don't want it to auto-restart.
        return if (Utils.isRunning) START_STICKY else START_NOT_STICKY
    }

    private fun createNotificationChannel() { // Definition of your method
        val serviceChannel = NotificationChannel(
            Consts.NOTIFICATION_CHANNEL_ID,
            Consts.NOTIFICATION_CHANNEL_NAME,
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
            Consts.PENDING_INTENT_REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent for the "Disconnect" action
        val disconnectIntent = Intent(this, TroadService::class.java).apply {
            action = Consts.ACTION_DISCONNECT
        }
        val pendingDisconnectIntent = PendingIntent.getService(
            this,
            Consts.PENDING_INTENT_REQUEST_CODE_DISCONNECT,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, Consts.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.troy) // Replace with your VPN icon
            .setContentTitle(getString(R.string.app_name) + " VPN") // App name + " VPN"
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Or PRIORITY_LOW for less intrusive
            .setContentIntent(pendingOpenAppIntent) // Action on tap
            .setOngoing(true) // Makes the notification non-dismissable by swiping
            .addAction(
                R.drawable.slash, // Replace with your disconnect icon (optional)
                "Disconnect", pendingDisconnectIntent
            )
        // .setPublicVersion(publicNotification) // For lock screen visibility control (optional)
        // .setProgress(0, 0, true) // Indeterminate progress (optional, if connecting)

        // For Android 8.0 (API 26) and higher, channel ID is required.
        // It's set in createNotificationChannel() and used by the builder.

        return notificationBuilder.build()
    }


    private fun prepareAndEstablishVpn(): Boolean {

        try {
            // --- This is a crucial part where you configure the VPN ---
            val builder = Builder()
            val tunIp = "10.8.0.2"
            val dns1 = "8.8.8.8"
            val dns2 = "8.8.4.4"
            // Configure IP address, routes, DNS servers, MTU, etc.
            // These are examples and MUST be configured according to your VPN server setup.
            builder.setSession(Rss.getString(R.string.app_name)) // Display name for the VPN session
                .addAddress(tunIp, 24)      // VPN client's virtual IP
                .addRoute("0.0.0.0", 0)          // Route all traffic through the VPN
                .addDnsServer(dns1).addDnsServer(dns2)
                .setMtu(1400)                      // Set MTU (adjust as needed)
            //  .addAllowedApplication("com.example.anotherapp") // For per-app VPN (optional)
            //  .addDisallowedApplication(packageName)           // Exclude this app (optional)

            // Optional: Configure an intent to open your app's settings if needed before connection
            // val configureIntent = Intent(this, YourVpnSettingsActivity::class.java)
            // builder.setConfigureIntent(PendingIntent.getActivity(this, 0, configureIntent, PendingIntent.FLAG_IMMUTABLE))

            vpnInterface = builder.establish() // This can return null if user denies permission
            if (vpnInterface == null) {
                Log.e(Consts.TAG, "VPN establish returned null. User might have denied permission.")
                sendBroadcast(
                    Intent(Consts.ACTION_VPN_STATUS_BROADCAST).putExtra(
                        "status", "PERMISSION_DENIED"
                    )
                )
                return false
            }

            // The key part: Create FileInputStream and FileOutputStream from the SAME FileDescriptor
            val fd = vpnInterface!!.fileDescriptor // This is java.io.FileDescriptor
            // For reading outgoing packets from the device
            vpnReadFileChannel = FileInputStream(fd).channel
            // For writing incoming packets to the device
            vpnWriteFileChannel = FileOutputStream(fd).channel
            Log.d(Consts.TAG, "VPN interface established.")
            return true
        } catch (e: Exception) {
            Log.e(Consts.TAG, "Error establishing VPN interface", e)
            // Notify UI about the error if needed
            sendBroadcast(
                Intent(Consts.ACTION_VPN_STATUS_BROADCAST).putExtra(
                    "status", "ERROR: ${e.localizedMessage}"
                )
            )
            return false
        }
    }

    private fun runVpnPacketLoop() {
        Log.i(Consts.TAG, "VPN Packet Loop thread started.")
        try {
            EmbeddedChannel().pipeline()
                .addLast(LoggingHandler(LogLevel.INFO))
                .addLast(InputHandler)
                .addLast(PacketDecoder)
                .addLast(DemuxHandler)
        } catch (e: InterruptedException) {
            isRunning = false
            throw RuntimeException(e)
        } finally {

        }

    }

    /**
     * Stops the VPN service, cleans up resources, and stops the foreground notification.
     * Call this when the VPN is meant to be fully shut down.
     * @param removeNotification Whether to explicitly remove the notification.
     *                           Usually true, but false if called during setup failure before notification is shown.
     */
    private fun stopVpnService(removeNotification: Boolean = true) {
        Log.i(Consts.TAG, "stopVpnService called. removeNotification: $removeNotification")
        isRunning = false // Signal loops and other operations to stop

        // Interrupt the VPN packet handling thread if it's running
        vpnReaderExecutor.shutdown()
        try {
            vpnReaderExecutor.awaitTermination(
                1000, MILLISECONDS
            ) // Wait for the thread to die for a short period
            if (!vpnReaderExecutor.isTerminated) {
                Log.w(Consts.TAG, "VPN packet thread did not terminate in time.")
            }
        } catch (e: InterruptedException) {
            Log.w(Consts.TAG, "Interrupted while waiting for VPN thread to join.")
            vpnReaderExecutor.shutdownNow()
            // Preserve interrupt status
        }

        closeVpnInterface() // Close the TUN interface

        // --- PLACEHOLDER: Close your actual remote VPN server connection here ---
        // if (remoteSocket != null && !remoteSocket.isClosed()) {
        //     try {
        //         remoteSocket.close()
        //         Log.i(TAG, "Remote VPN server socket closed.")
        //     } catch (e: IOException) {
        //         Log.w(TAG, "IOException closing remote socket", e)
        //     }
        // }
        // remoteSocket = null
        // remoteInStream = null
        // remoteOutStream = null
        // --- END OF PLACEHOLDER ---

        if (removeNotification) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.d(Consts.TAG, "Foreground service stopped and notification removed.")
        } else {
            // If called due to setup failure before startForeground,
            // we might not need to call stopForeground if it was never started.
            // However, calling stopSelf ensures the service itself stops if it's in a startable state.
            Log.d(
                Consts.TAG,
                "stopVpnService: Notification not explicitly removed (or may not have been shown)."
            )
        }

        stopSelf() // Stop the service itself
        Log.i(Consts.TAG, "VPN Service stopped.")
        broadcastVpnStatus("Disconnected", false) // Notify UI
    }

    /**
     * Initiates the disconnection sequence.
     * Can be called from an intent or internally.
     */
    private fun disconnectVpn() {
        Log.i(Consts.TAG, "disconnectVpn called.")
        if (!Utils.isRunning) {
            Log.d(Consts.TAG, "VPN is not running, no need to disconnect further.")
            // Ensure service stops if it's lingering without being fully connected
            if (vpnInterface == null) stopSelf()
            return
        }
        broadcastVpnStatus("Disconnecting...", false)
        stopVpnService(true) // True to remove notification
    }

    /**
     * Broadcasts the VPN status (message and connection state).
     *
     * @param message A descriptive message about the current status (e.g., "Connecting...", "Connected", "Error").
     * @param connected True if the VPN is considered connected, false otherwise.
     */
    private fun broadcastVpnStatus(message: String, connected: Boolean) {
        val intent = Intent(Consts.ACTION_VPN_STATUS_BROADCAST).apply {
            putExtra(Consts.EXTRA_STATUS_MESSAGE, message)
            putExtra(Consts.EXTRA_IS_CONNECTED, connected)
            // Optional: If this broadcast is only meant for components within your app,
            // you can make it more secure and efficient by setting the package.
            // This prevents other apps from intercepting it.
            // setPackage(packageName)
        }
        sendBroadcast(intent) // Sends a system-wide broadcast
        Log.d(Consts.TAG, "VPN status broadcast: '$message', Connected: $connected")

    }

    /**
     * Closes the local VPN interface (ParcelFileDescriptor).
     */
    private fun closeVpnInterface() {
        vpnInterface?.let {
            try {
                it.close()
                Log.d(Consts.TAG, "VPN interface (ParcelFileDescriptor) closed.")
            } catch (e: IOException) {
                Log.e(Consts.TAG, "IOException closing VPN interface", e)
            }
        }
        vpnInterface = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(Consts.TAG, "VPN Service Destroyed.")
        // Ensure all resources are cleaned up if not already done.
        // This is a final safeguard.
        if (isRunning || vpnInterface != null || !vpnReaderExecutor.isTerminated) {
            Log.w(
                Consts.TAG, "onDestroy: Forcing cleanup as service might not have stopped cleanly."
            )
            stopVpnService(true)
        }
    }

}