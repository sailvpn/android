package com.illiad.troad.model

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
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

    var currentScreen by mutableStateOf(Screen.Main)
        private set

    fun startProxyService() {
        val startTroad = Intent(app.applicationContext, TroadService::class.java).apply {
            action = ACTION_CONNECT
            // Note: We don't need to pass Extras if the Service reads from TroadStore!
        }
        ContextCompat.startForegroundService(app, startTroad)

    }

    // ... rest of the ViewModel (stopProxyService, updateVpnStatus)

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

    // 2. Fallback helper mapping to support your background notification receiver threads
    fun updateVpnStatus(isConnected: Boolean, message: String?) {
        isProxyRunning = isConnected

        vpnState = when {
            isConnected -> VpnStatus.Connected(downloadSpeed = "12.4 Mbps", uploadSpeed = "4.1 Mbps")
            message?.contains("Connecting", ignoreCase = true) == true -> VpnStatus.Connecting(message)
            message?.contains("Disconnected", ignoreCase = true) == true -> VpnStatus.Disconnected
            !message.isNullOrBlank() -> VpnStatus.Error(message)
            else -> VpnStatus.Disconnected
        }
    }
}
