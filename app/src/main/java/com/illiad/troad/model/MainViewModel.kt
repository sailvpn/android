package com.illiad.troad.model

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.illiad.troad.Consts.ACTION_CONNECT
import com.illiad.troad.Consts.ACTION_DISCONNECT
import com.illiad.troad.service.TroadService

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    // UI state for the Main screen only
    var isProxyRunning by mutableStateOf(false)
        private set

    // 1. Maintain a single source of truth using the sealed model type
    var vpnState: VpnStatus by mutableStateOf(VpnStatus.Disconnected)
        private set

    // NEW: Persistent error queue for the global dialog (Observed reactively by Compose)
    val errorQueue = mutableStateListOf<String>()

    var currentScreen by mutableStateOf(Screen.Main)
        private set

    fun startProxyService() {
        val startTroad = Intent(app.applicationContext, TroadService::class.java).apply {
            action = ACTION_CONNECT
        }
        ContextCompat.startForegroundService(app, startTroad)
    }

    fun stopProxyService() {
        Log.d("ViewModel", "Stopping proxy service")
        val stopTroad = Intent(app.applicationContext, TroadService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        ContextCompat.startForegroundService(app, stopTroad)
    }

    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }

    /**
     * DISMISS ACTION: Safely pops the top error out of the snapshot array.
     * Recomposes your global Compose Dialog overlay layout automatically.
     */
    fun dismissError() {
        if (errorQueue.isNotEmpty()) {
            errorQueue.removeAt(0) // First-In, First-Out (FIFO) queue popping
        }
    }

    // Inside your MainViewModel class

    fun updateVpnStatus(state: VpnState, message: String?) {
        // 1. Maintain your primary background running status flag
        isProxyRunning = (state == VpnState.CONNECTED || state == VpnState.SPEED)

        // 2. Map the simplified VpnState enum directly into your rich Composable VpnStatus object
        val newState = when (state) {
            VpnState.SPEED -> {
                // Check if the metric payload contains your standard string delimiter
                if (!message.isNullOrBlank() && message.contains("|")) {
                    val speeds = message.split("|")
                    val download = speeds.getOrNull(0) ?: "0.0 Mbps"
                    val upload = speeds.getOrNull(1) ?: "0.0 Mbps"

                    // Stream updated metrics directly to the view layout
                    VpnStatus.Connected(downloadSpeed = download, uploadSpeed = upload)
                } else {
                    // If payload parsing drops, fall back to the existing snapshot state parameters
                    vpnState
                }
            }

            VpnState.CONNECTED -> {
                // Core connected transition baseline initialization
                VpnStatus.Connected(downloadSpeed = "0.0 Mbps", uploadSpeed = "0.0 Mbps")
            }

            VpnState.CONNECTING -> {
                VpnStatus.Connecting(message ?: "Connecting...")
            }

            VpnState.RECONNECTING -> {
                VpnStatus.Connecting(message ?: "Reconnecting...")
            }

            VpnState.ERROR -> {
                VpnStatus.Error(message ?: "An unexpected network exception occurred.")
            }

            VpnState.DISCONNECTED -> {
                VpnStatus.Disconnected
            }
        }

        // 3. Persistent Queue Management: Append to your reactive list
        // to pop up the Compose AlertDialog window automatically
        if (newState is VpnStatus.Error) {
            errorQueue.add(newState.message)
        }

        // 4. Trigger native Jetpack Compose re-compositions across your screen widgets
        vpnState = newState
    }


}

