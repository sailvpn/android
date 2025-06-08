package com.illiad.troad.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.illiad.troad.Consts
import com.illiad.troad.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread // For background network operations

// Assume you have these resources:
// R.string.app_name - Your app's name
// R.drawable.ic_vpn_notification - Notification icon
// R.drawable.ic_disconnect_notification - Disconnect action icon (optional)

class TroadService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null // Thread for handling VPN packet I/O
    private var isRunning = false

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
                if (isRunning) {
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
                    isRunning = true
                    startForeground(Consts.NOTIFICATION_ID, createNotification("VPN Connected"))

                    // Start a thread to handle packet forwarding
                    vpnThread = thread { // Using kotlin.concurrent.thread for simplicity
                        runVpnPacketLoop()
                    }
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
        return if (isRunning) START_STICKY else START_NOT_STICKY
    }

    private fun createNotificationChannel() { // Definition of your method
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                Consts.NOTIFICATION_CHANNEL_ID,
                Consts.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel) // Good to add null check for manager
        }
    }

    private fun createNotification(contentText: String): Notification {
        // Intent to open the app when the notification is tapped
        val openAppIntent = Intent(
            this,
            MainActivity::class.java
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
            .setSmallIcon(R.drawable.ic_vpn_notification) // Replace with your VPN icon
            .setContentTitle(getString(R.string.app_name) + " VPN") // App name + " VPN"
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Or PRIORITY_LOW for less intrusive
            .setContentIntent(pendingOpenAppIntent) // Action on tap
            .setOngoing(true) // Makes the notification non-dismissable by swiping
            .addAction(
                R.drawable.ic_disconnect_notification, // Replace with your disconnect icon (optional)
                "Disconnect",
                pendingDisconnectIntent
            )
        // .setPublicVersion(publicNotification) // For lock screen visibility control (optional)
        // .setProgress(0, 0, true) // Indeterminate progress (optional, if connecting)

        // For Android 8.0 (API 26) and higher, channel ID is required.
        // It's set in createNotificationChannel() and used by the builder.

        return notificationBuilder.build()
    }


    private fun prepareAndEstablishVpn(): Boolean {
        // --- This is a crucial part where you configure the VPN ---
        val builder = Builder()
        try {
            // Configure IP address, routes, DNS servers, MTU, etc.
            // These are examples and MUST be configured according to your VPN server setup.
            builder.setSession(getString(R.string.app_name)) // Display name for the VPN session
                .addAddress("10.8.0.2", 24)      // VPN client's virtual IP
                .addRoute("0.0.0.0", 0)          // Route all traffic through the VPN
                .addDnsServer("8.8.8.8")           // Example DNS
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
                        "status",
                        "PERMISSION_DENIED"
                    )
                )
                return false
            }
            Log.d(Consts.TAG, "VPN interface established.")
            return true
        } catch (e: Exception) {
            Log.e(Consts.TAG, "Error establishing VPN interface", e)
            // Notify UI about the error if needed
            sendBroadcast(
                Intent("com.example.myvpnapp.VPN_STATUS").putExtra(
                    "status",
                    "ERROR: ${e.localizedMessage}"
                )
            )
            return false
        }
    }

    private fun runVpnPacketLoop() {
        Log.i(Consts.TAG, "VPN Packet Loop thread started.")
        val localVpnInterface = vpnInterface ?: run {
            Log.e(Consts.TAG, "Cannot start packet loop, VPN interface is null.")
            broadcastVpnStatus("Error: VPN interface lost.", false)
            disconnectVpn() // Ensure full cleanup
            return
        }

        // These streams are for reading from and writing to the local TUN interface.
        // Data written to vpnOutput goes to the Android OS network stack (as if coming from the network).
        // Data read from vpnInput comes from the Android OS network stack (apps sending data out).
        val vpnInput = FileInputStream(localVpnInterface.fileDescriptor)
        val vpnOutput = FileOutputStream(localVpnInterface.fileDescriptor)

        // Buffer for IP packets. Max IP packet size is typically 65535,
        // but smaller buffers (e.g., 32767 or based on MTU) are common.
        val buffer =
            ByteArray(32767) // OrShort.MAX_VALUE.toInt() for full range, but MTU is usually smaller

        // --- PLACEHOLDER: Establish and maintain connection to your remote VPN server ---
        // Example:
        // var remoteSocket: Socket? = null
        // var remoteOutStream: OutputStream? = null
        // var remoteInStream: InputStream? = null
        // try {
        //     remoteSocket = Socket(serverAddress, serverPort) // This is a blocking call!
        //     remoteOutStream = remoteSocket.getOutputStream()
        //     remoteInStream = remoteSocket.getInputStream()
        //     Log.i(TAG, "Connected to remote VPN server: $serverAddress:$serverPort")
        //
        //     // You might need a separate thread to read from remoteInStream and write to vpnOutput
        //     // to handle simultaneous bidirectional traffic.
        //
        // } catch (e: IOException) {
        //     Log.e(TAG, "Failed to connect to remote VPN server: $serverAddress:$serverPort", e)
        //     broadcastVpnStatus("Error: Could not connect to remote server.", false)
        //     disconnectVpn()
        //     return
        // }
        // --- END OF REMOTE CONNECTION PLACEHOLDER ---

        Log.i(Consts.TAG, "VPN Packet Loop now running...")

        try {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                // Read outgoing IP packet from the Android OS (tun0 interface)
                val bytesRead = vpnInput.read(buffer)
                if (bytesRead > 0) {
                    val outgoingPacket = buffer.copyOfRange(0, bytesRead)
                    Log.d(Consts.TAG, "OUTGOING: Read $bytesRead bytes from TUN.")

                    // --- PLACEHOLDER: Encrypt/Encapsulate `outgoingPacket` ---
                    // val encryptedPacket = encryptPacket(outgoingPacket, sharedSecret) // Your encryption logic
                    // --- END OF PLACEHOLDER ---

                    // --- PLACEHOLDER: Send `encryptedPacket` to your remote VPN server ---
                    // try {
                    //    remoteOutStream?.write(encryptedPacket)
                    //    remoteOutStream?.flush()
                    //    Log.d(TAG, "OUTGOING: Sent ${encryptedPacket.size} encrypted bytes to server.")
                    // } catch (e: IOException) {
                    //    Log.e(TAG, "IOException writing to remote server", e)
                    //    broadcastVpnStatus("Error: Connection to server lost (write).", false)
                    //    break // Exit loop on connection error
                    // }
                    // --- END OF PLACEHOLDER ---

                    // --- SIMPLIFIED ECHO (REMOVE FOR REAL VPN) ---
                    // For testing the TUN interface locally without a real server,
                    // you might echo the packet back. THIS IS NOT A VPN.
                    Log.w(
                        Consts.TAG,
                        "ECHOING PACKET (REMOVE FOR REAL VPN): Writing $bytesRead bytes back to TUN."
                    )
                    vpnOutput.write(outgoingPacket, 0, bytesRead) // Echo back for testing
                    // --- END OF ECHO ---
                }

                // --- PLACEHOLDER: Receive data from your remote VPN server ---
                // This part is often handled in a separate thread or using non-blocking I/O
                // to avoid blocking the reading from vpnInput.
                // Example (simplified, would block if no data):
                // if (remoteInStream != null && remoteInStream.available() > 0) {
                //     val bytesFromServer = remoteInStream.read(buffer)
                //     if (bytesFromServer > 0) {
                //         val encryptedIncomingPacket = buffer.copyOfRange(0, bytesFromServer)
                //         Log.d(TAG, "INCOMING: Received $bytesFromServer encrypted bytes from server.")
                //
                //         // --- PLACEHOLDER: Decrypt/Decapsulate `encryptedIncomingPacket` ---
                //         // val incomingPacket = decryptPacket(encryptedIncomingPacket, sharedSecret) // Your decryption logic
                //         // --- END OF PLACEHOLDER ---
                //
                //         // if (incomingPacket != null) {
                //         //    vpnOutput.write(incomingPacket) // Write to TUN
                //         //    Log.d(TAG, "INCOMING: Wrote ${incomingPacket.size} decrypted bytes to TUN.")
                //         // }
                //     } else if (bytesFromServer == -1) { // Server closed connection
                //         Log.i(TAG, "Remote server closed connection.")
                //         broadcastVpnStatus("Server disconnected.", false)
                //         break // Exit loop
                //     }
                // }
                // --- END OF PLACEHOLDER ---

                // Prevent busy-waiting if no data is immediately available from either side.
                // In a real implementation with blocking sockets, reads will block appropriately.
                // If using non-blocking I/O, selectors (NIO) would be used.
                // Thread.sleep(10) // Small delay; better to use blocking I/O or NIO selectors.
            }
        } catch (e: IOException) {
            if (isRunning) { // Only log as error if we were supposed to be running
                Log.e(Consts.TAG, "IOException in VPN packet loop", e)
                broadcastVpnStatus("Network error in VPN.", false)
            } else {
                Log.i(Consts.TAG, "IOException in VPN packet loop (service stopping): ${e.message}")
            }
        } catch (e: InterruptedException) {
            Log.i(Consts.TAG, "VPN packet loop interrupted.")
            Thread.currentThread().interrupt() // Preserve interrupt status
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(Consts.TAG, "Unexpected error in VPN packet loop", e)
                broadcastVpnStatus("Unexpected error in VPN.", false)
            } else {
                Log.i(Consts.TAG, "Unexpected error in VPN packet loop (service stopping): ${e.message}")
            }
        } finally {
            Log.i(Consts.TAG, "VPN Packet Loop exiting.")
// --- PLACEHOLDER: Close remote connection ---
// try {
//     remoteInStream?.close()
//     remoteOutStream?.close()
//     remoteSocket?.close()
//     Log.i(TAG, "Closed connection to remote VPN server.")
// } catch (e: IOException) {
//     Log.w(TAG, "IOException closing remote server connection", e)
// }
// --- END OF

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
        vpnThread?.interrupt()
        try {
            vpnThread?.join(1000) // Wait for the thread to die for a short period
            if (vpnThread?.isAlive == true) {
                Log.w(Consts.TAG, "VPN packet thread did not terminate in time.")
            }
        } catch (e: InterruptedException) {
            Log.w(Consts.TAG, "Interrupted while waiting for VPN thread to join.")
            Thread.currentThread().interrupt() // Preserve interrupt status
        }
        vpnThread = null

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
            stopForeground(true) // True to remove the notification
            // Or use stopForeground(STOP_FOREGROUND_REMOVE) for Android 13+
            Log.d(Consts.TAG, "Foreground service stopped and notification removed.")
        } else {
            // If called due to setup failure before startForeground,
            // we might not need to call stopForeground if it was never started.
            // However, calling stopSelf ensures the service itself stops if it's in a startable state.
            Log.d(Consts.TAG, "stopVpnService: Notification not explicitly removed (or may not have been shown).")
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
        if (!isRunning) {
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
        if (isRunning || vpnInterface != null || vpnThread?.isAlive == true) {
            Log.w(Consts.TAG, "onDestroy: Forcing cleanup as service might not have stopped cleanly.")
            stopVpnService(true)
        }
    }


}