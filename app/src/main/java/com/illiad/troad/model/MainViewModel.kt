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
    var vpnStatusMessage by mutableStateOf("Disconnected")
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

    fun updateVpnStatus(isConnected: Boolean, message: String?) {
        isProxyRunning = isConnected
        vpnStatusMessage = message ?: if (isConnected) "Connected" else "Disconnected"
    }
}
