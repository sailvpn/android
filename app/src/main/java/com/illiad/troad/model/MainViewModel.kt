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
import kotlinx.serialization.json.Json

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

    // Holds the optimized presentation model for targeted recomposition paths
    var speedMetrics by mutableStateOf(SpeedMetrics())
        private set

    private val jsonParser = Json { ignoreUnknownKeys = true }

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

    fun updateVpnStatus(state: VpnState, message: String?) {
        isProxyRunning = (state == VpnState.CONNECTED || state == VpnState.SPEED)

        // 1. FAST PATH FOR SPEED METRICS PROCESSING:
        if (state == VpnState.SPEED) {
            if (!message.isNullOrBlank()) {
                try {
                    val parsedMetrics = jsonParser.decodeFromString<SpeedMetricsPayload>(message)

                    // Format numeric raw bytes cleanly right at the presentation boundary
                    speedMetrics = SpeedMetrics(
                        down = BandwidthFormatter.formatSpeed(parsedMetrics.down),
                        up = BandwidthFormatter.formatSpeed(parsedMetrics.up)
                    )
                } catch (e: Exception) {
                    android.util.Log.e("ViewModel", "Failed parsing numeric Speed JSON", e)
                }
            }
            return // Exit execution early to lock out heavy background canvas re-paints
        }

        // 2. STANDARD LIFE-CYCLE STATE MACHINE ROUTING:
        val newState = when (state) {
            VpnState.CONNECTED -> VpnStatus.Connected()
            VpnState.CONNECTING -> VpnStatus.Connecting(message ?: "Connecting...")
            VpnState.RECONNECTING -> VpnStatus.Connecting(message ?: "Reconnecting...")
            VpnState.ERROR -> VpnStatus.Error(message ?: "An unexpected error occurred.")
            VpnState.DISCONNECTED -> {
                speedMetrics = SpeedMetrics() // Reset text meters to baseline zero strings instantly
                VpnStatus.Disconnected
            }
            else -> VpnStatus.Disconnected
        }

        if (newState is VpnStatus.Error) {
            errorQueue.add(newState.message)
        }

        vpnState = newState
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

}

