package com.illiad.troad.model

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.illiad.troad.Consts.ACTION_CONNECT
import com.illiad.troad.Consts.ACTION_DISCONNECT
import com.illiad.troad.service.SettingsRepoImp
import com.illiad.troad.service.SettingsUseCase
import com.illiad.troad.service.TroadService
import com.illiad.troad.service.security.Cryptos
import com.illiad.troad.service.security.client.TokenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.periodUntil
import troadengine.Troadengine

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    // UI state for the START/STOP toggle button styling
    var isProxyRunning by mutableStateOf(false)
        private set

    // Strict status state machine synchronized with the TroadService lifecycle broadcasts
    var vpnState: VpnStatus by mutableStateOf(VpnStatus.Disconnected)
        private set

    // Persistent error alert queue observed reactively by Compose AlertDialog overlays
    val errorQueue = mutableStateListOf<String>()

    // 1. FIXED NAVIGATION STATE: Driven by Compose mutableStateOf for automatic UI re-composition
    var currentScreen by mutableStateOf(Screen.Main)
        private set

    var cryptoStatus by mutableStateOf(CryptoStatus())
        private set

    private var cryptoInfoJob: Job? = null

    init {
        // Synchronize initial state with the native engine
        try {
            if (Troadengine.isRunning()) {
                isProxyRunning = true
                vpnState = VpnStatus.Connected()
            }
        } catch (e: Throwable) {
            Log.e("MainViewModel", "Failed to check initial engine state", e)
        }
        startCryptoInfoObserver()
    }

    private fun startCryptoInfoObserver() {
        val tStore = TroadStore(app)
        val settingsUseCase = SettingsUseCase(SettingsRepoImp(tStore))
        val tokenManager = TokenManager.getInstance(app)

        cryptoInfoJob?.cancel()
        cryptoInfoJob = viewModelScope.launch {
            settingsUseCase().collectLatest { settings ->
                while (isActive) {
                    cryptoStatus = when (settings.crypto) {
                        Cryptos.JWT2 -> CryptoStatus("JWT2")
                        Cryptos.JWT -> {
                            val now = Clock.System.now()
                            val expiry = tokenManager.getExpireInstant(settings.jwt)
                            val remaining = expiry - now
                            
                            if (remaining.isNegative()) {
                                CryptoStatus("JWT: Expired", Color(0xFFF44336))
                            } else {
                                val period = now.periodUntil(expiry, TimeZone.currentSystemDefault())
                                val parts = mutableListOf<String>()
                                if (period.months > 0) parts.add("${period.months}mo")
                                if (period.days > 0) parts.add("${period.days}d")
                                if (period.hours > 0) parts.add("${period.hours}h")
                                if (period.minutes > 0) parts.add("${period.minutes}m")
                                
                                val label = "JWT: " + parts.joinToString(" ")
                                val color = when {
                                    remaining.inWholeDays < 1 -> Color(0xFFF44336) // Red
                                    remaining.inWholeDays < 7 -> Color(0xFFFFC107) // Yellow
                                    else -> Color(0xFF4CAF50) // Green
                                }
                                CryptoStatus(label, color)
                            }
                        }
                        else -> CryptoStatus(settings.crypto.value)
                    }
                    if (settings.crypto == Cryptos.JWT) {
                        delay(60000)
                    } else {
                        break
                    }
                }
            }
        }
    }

    /**
     * 2. FIXED NAVIGATION ROUTER: Updates the current screen target state.
     * Called directly from MainActivity.kt and MainView.kt to toggle view contexts.
     */
    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }

    fun startProxyService() {
        Log.d("ViewModel", "User clicked START. Initiating tactile intent toggle...")
        isProxyRunning = true
        vpnState = VpnStatus.Connecting("Connecting...")

        viewModelScope.launch {
            delay(300) // Tactile feel delay window
            if (isProxyRunning) {
                val startTroad = Intent(app.applicationContext, TroadService::class.java).apply {
                    action = ACTION_CONNECT
                }
                ContextCompat.startForegroundService(app, startTroad)
            }
        }
    }

    fun stopProxyService() {
        Log.d("ViewModel", "User clicked STOP. Terminating/aborting service pipeline...")
        isProxyRunning = false
        vpnState = VpnStatus.Disconnected

        viewModelScope.launch {
            delay(150)
            val stopTroad = Intent(app.applicationContext, TroadService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            // Use startService instead of startForegroundService for commands to an already running service,
            // especially when the command is intended to STOP the service.
            app.startService(stopTroad)
        }
    }

    fun dismissError() {
        if (errorQueue.isNotEmpty()) {
            errorQueue.removeAt(0)
        }
    }

    fun updateVpnStatus(state: VpnState, message: String?) {
        val newState = when (state) {
            VpnState.CONNECTED -> {
                isProxyRunning = true
                VpnStatus.Connected()
            }

            VpnState.CONNECTING -> {
                if (!isProxyRunning) return
                VpnStatus.Connecting(message ?: "Connecting...")
            }

            VpnState.RECONNECTING -> {
                if (!isProxyRunning) return
                VpnStatus.Connecting(message ?: "Reconnecting...")
            }

            VpnState.ERROR -> {
                isProxyRunning = false
                VpnStatus.Error(message ?: "An unexpected error occurred.")
            }

            VpnState.DISCONNECTED -> {
                isProxyRunning = false
                VpnStatus.Disconnected
            }

            VpnState.SPEED -> vpnState
        }

        if (newState is VpnStatus.Error) {
            errorQueue.add(newState.message)
        }

        vpnState = newState
    }
}


