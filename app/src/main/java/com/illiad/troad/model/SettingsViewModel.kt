package com.illiad.troad.model

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.illiad.troad.Consts.ACTION_CONNECT
import com.illiad.troad.Consts.ACTION_DISCONNECT
import com.illiad.troad.service.TroadService
import com.illiad.troad.service.security.Cryptos
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SettingsViewModel(
    private val app: Application, private val tStore: TroadStore
) : AndroidViewModel(app) {

    var currentScreen by mutableStateOf(Screen.Main)
        private set

    var vpnStatusMessage by mutableStateOf("Disconnected")
        private set

    // --- Raw UI Input StateFlows (what the user is typing in TextFields) ---
    private val _uiServerDomainInput = MutableStateFlow("")
    val uiServerDomainInput = _uiServerDomainInput.asStateFlow()

    private val _uiServerPortInput = MutableStateFlow("")
    val uiServerPortInput = _uiServerPortInput.asStateFlow()

    private val _uiSharedSecretInput = MutableStateFlow("")
    val uiSharedSecretInput = _uiSharedSecretInput.asStateFlow()

    // Expose the current selection from the store to the UI
    val selectedCrypto: StateFlow<Cryptos> = tStore.selectedCryptoFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Cryptos.JWT2)

    // The list of items for the 1-of-n selection
    val cryptoOptions = Cryptos.AvailableCryptos

    fun onCryptoSelected(newCrypto: Cryptos) {
        viewModelScope.launch {
            tStore.saveCryptoSelection(newCrypto)
        }
    }

    // --- Debounced UI State (for internal logic AND potentially for UI if needed) ---
    // These are updated after debouncing. The UI can observe these if it needs
    // to react to the debounced state, or it can just rely on errorMessage.
    var debouncedUiServerDomain by mutableStateOf("")
        private set // UI can read this if it's made public or through a getter
    var debouncedUiServerPort by mutableStateOf("")
        private set
    var debouncedUiSharedSecret by mutableStateOf("")
        private set

    // --- StateFlows from DataStore ---
    // ... (tStoreServerDomain, tStoreServerPort, tStoreSharedSecret remain the same) ...

    var isProxyRunning by mutableStateOf(false)

    // No private set if ProxySettingsScreen needs to observe this directly for UI changes
    // Or if changes are propagated via a method that UI calls after an action
    var errorMessage by mutableStateOf<String?>(null)
    // This is already public and will be observed by the UI

    private val validationDebounceMillis = 300L

    init {
        // 1. Initial Load: Populate the UI from the Store
        viewModelScope.launch {
            val initialDomain = tStore.serverDomainFlow.first()
            val initialPort =
                tStore.serverPortFlow.first().let { if (it == 0) "" else it.toString() }
            val initialSecret = tStore.sharedSecretFlow.first()

            _uiServerDomainInput.value = initialDomain
            _uiServerPortInput.value = initialPort
            _uiSharedSecretInput.value = initialSecret

            // Sync internal debounced state
            debouncedUiServerDomain = initialDomain
            debouncedUiServerPort = initialPort
            debouncedUiSharedSecret = initialSecret

        }

        // 2. Debounced DOMAIN: Auto-save to Store
        viewModelScope.launch {
            _uiServerDomainInput
                .debounce(validationDebounceMillis)
                .collectLatest { domain ->
                    debouncedUiServerDomain = domain
                    if (validateDomain()) {
                        tStore.saveServerDomain(domain) // Save to Store
                    }
                }
        }

        // 3. Debounced PORT: Auto-save to Store
        viewModelScope.launch {
            _uiServerPortInput
                .debounce(validationDebounceMillis)
                .collectLatest { portString ->
                    debouncedUiServerPort = portString
                    if (validatePort()) {
                        val portInt = portString.toIntOrNull() ?: 1080
                        tStore.saveServerPort(portInt) // Save to Store
                    }
                }
        }

        // 4. Debounced SECRET: Auto-save to Store
        viewModelScope.launch {
            _uiSharedSecretInput
                .debounce(validationDebounceMillis)
                .collectLatest { secret ->
                    debouncedUiSharedSecret = secret
                    if (validateSecret()) {
                        tStore.saveSharedSecret(secret) // Save to Store
                    }
                }
        }
    }

    private fun validateDomain(): Boolean {
        // Use debouncedUiServerDomain for validation
        if (debouncedUiServerDomain.isBlank()) {
            errorMessage = "Server domain cannot be empty."
            return false
        }
        if (errorMessage == "Server domain cannot be empty.") errorMessage = null
        return true
    }

    private fun validatePort(): Boolean {
        // Use debouncedUiServerPort for validation
        if (debouncedUiServerPort.isBlank()) {
            errorMessage = "Server port cannot be empty."
            return false
        }
        val port = debouncedUiServerPort.toIntOrNull()
        if (port == null || port !in 1..65535) {
            errorMessage = "Invalid port number. Must be between 1 and 65535."
            return false
        }
        if (errorMessage == "Server port cannot be empty." || errorMessage?.startsWith("Invalid port") == true) errorMessage =
            null
        return true
    }

    private fun validateSecret(): Boolean {
        // Use debouncedUiSharedSecret
        // if (debouncedUiSharedSecret.isBlank()) {
        //     errorMessage = "Shared secret cannot be empty."
        //     return false
        // }
        // if (errorMessage == "Shared secret cannot be empty.") errorMessage = null
        return true
    }


    fun onDomainChange(newDomain: String) {
        _uiServerDomainInput.value = newDomain
    }

    fun onPortChange(newPortString: String) {
        _uiServerPortInput.value = newPortString
    }

    fun onSecretChange(newSecret: String) {
        _uiSharedSecretInput.value = newSecret
    }

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