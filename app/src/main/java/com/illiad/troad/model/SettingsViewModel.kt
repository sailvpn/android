package com.illiad.troad.model

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.illiad.troad.Consts.ACTION_CONNECT
import com.illiad.troad.Consts.ACTION_DISCONNECT
import com.illiad.troad.Consts.EXTRA_SERVER_ADDRESS
import com.illiad.troad.Consts.EXTRA_SERVER_PORT
import com.illiad.troad.Consts.EXTRA_SHARED_SECRET
import com.illiad.troad.service.TroadService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SettingsViewModel(
    private val app: Application, private val tStore: TroadStore
) : AndroidViewModel(app) {

    // --- Raw UI Input StateFlows (what the user is typing in TextFields) ---
    private val _uiServerDomainInput = MutableStateFlow("")
    val uiServerDomainInput = _uiServerDomainInput.asStateFlow()

    private val _uiServerPortInput = MutableStateFlow("")
    val uiServerPortInput = _uiServerPortInput.asStateFlow()

    private val _uiSharedSecretInput = MutableStateFlow("")
    val uiSharedSecretInput = _uiSharedSecretInput.asStateFlow()

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
        viewModelScope.launch {
            val initialDomain = tStore.serverDomainFlow.first()
            val initialPort = tStore.serverPortFlow.first().let { if (it == 0) "" else it.toString() }
            val initialSecret = tStore.sharedSecretFlow.first()

            _uiServerDomainInput.value = initialDomain
            _uiServerPortInput.value = initialPort
            _uiSharedSecretInput.value = initialSecret

            debouncedUiServerDomain = initialDomain
            debouncedUiServerPort = initialPort
            debouncedUiSharedSecret = initialSecret

            validateAllInputs()
        }

        viewModelScope.launch {
            _uiServerDomainInput
                .debounce(validationDebounceMillis)
                .collectLatest { domain ->
                    debouncedUiServerDomain = domain // Update the debounced state
                    validateDomain()
                }
        }
        viewModelScope.launch {
            _uiServerPortInput
                .debounce(validationDebounceMillis)
                .collectLatest { portString ->
                    debouncedUiServerPort = portString // Update the debounced state
                    validatePort()
                }
        }
        viewModelScope.launch {
            _uiSharedSecretInput
                .debounce(validationDebounceMillis)
                .collectLatest { secret ->
                    debouncedUiSharedSecret = secret // Update the debounced state
                    validateSecret()
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
        if (errorMessage == "Server port cannot be empty." || errorMessage?.startsWith("Invalid port") == true) errorMessage = null
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


    private fun validateAllInputs(): Boolean {
        if (!validateDomain()) return false
        if (!validatePort()) return false
        if (!validateSecret()) return false
        errorMessage = null
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
        if (validateAllInputs()) { // This now uses the debounced values internally
            viewModelScope.launch {
                tStore.saveServerDomain(debouncedUiServerDomain) // Save debounced
                debouncedUiServerPort.toIntOrNull()?.let { portInt -> tStore.saveServerPort(portInt) }
                tStore.saveSharedSecret(debouncedUiSharedSecret)

                val startTroad = Intent(app.applicationContext, TroadService::class.java).apply {
                    action = ACTION_CONNECT
                    putExtra(EXTRA_SERVER_ADDRESS, debouncedUiServerDomain)
                    putExtra(EXTRA_SERVER_PORT, debouncedUiServerPort.toIntOrNull() ?: 0)
                    putExtra(EXTRA_SHARED_SECRET, debouncedUiSharedSecret)
                }
                app.startService(startTroad)
                isProxyRunning = true
            }
        }
    }
    // ... rest of the ViewModel (stopProxyService, updateVpnStatus)

    fun stopProxyService() {
        Log.d("ViewModel", "Stopping proxy service")
        val stopTroad = Intent(app.applicationContext, TroadService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        app.startService(stopTroad)
        isProxyRunning = false
    }

    fun updateVpnStatus(isConnected: Boolean, message: String?) {
        // reduce the fliping of display
        if (isProxyRunning != isConnected) {
            isProxyRunning = isConnected
            // Update any other relevant UI state based on VPN status
        }
    }
}