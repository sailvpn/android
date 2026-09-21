package com.illiad.troad.model

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.illiad.troad.Consts.VM
import com.illiad.troad.service.SettingsRepoImp
import com.illiad.troad.service.SettingsUseCase
import com.illiad.troad.service.security.Cryptos
import com.illiad.troad.service.security.client.TokenManager
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

    // --- Raw UI Input StateFlows (what the user is typing in TextFields) ---
    private val _uiServerDomainInput = MutableStateFlow("")
    val uiServerDomainInput = _uiServerDomainInput.asStateFlow()

    private val _uiSniInput = MutableStateFlow("")
    val uiSniInput = _uiSniInput.asStateFlow()


    private val _uiServerPortInput = MutableStateFlow("")
    val uiServerPortInput = _uiServerPortInput.asStateFlow()

    private val _uiSharedSecretInput = MutableStateFlow("")
    val uiSharedSecretInput = _uiSharedSecretInput.asStateFlow()

    private val _username = MutableStateFlow("")
    private val _password = MutableStateFlow("")

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

    val duration: StateFlow<Duration> = tStore.durationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Duration.DEFAULT)

    fun onDurationChanged(changed: Duration) {
        viewModelScope.launch {
            try {
                tStore.saveDuration(changed)
                Log.d(VM, "Duratiuon saved successfully")
            } catch (e: Exception) {
                errorMessage = "Failed to save JWT: ${e.localizedMessage}"
            }
        }
    }

    val autoRenewOptions = AutoRenew.entries
    val autoRenew: StateFlow<AutoRenew> = tStore.autoRenewFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutoRenew.DEFAULT)

    fun onAutoRenewChanged(changed: AutoRenew) {
        viewModelScope.launch {
            tStore.saveAutoRenew(changed)
        }
    }

    val username: StateFlow<String> = _username.asStateFlow()

    val password: StateFlow<String> = _password.asStateFlow()


    // --- Debounced UI State (for internal logic AND potentially for UI if needed) ---
    // These are updated after debouncing. The UI can observe these if it needs
    // to react to the debounced state, or it can just rely on errorMessage.
    var debouncedUiServerDomain by mutableStateOf("")
        private set // UI can read this if it's made public or through a getter
    var debouncedUiSni by mutableStateOf("")
        private set
    var debouncedUiServerPort by mutableStateOf("")
        private set
    var debouncedUiSharedSecret by mutableStateOf("")
        private set

    // --- StateFlows from DataStore ---
    // ... (tStoreServerDomain, tStoreServerPort, tStoreSharedSecret remain the same) ...

    // No private set if ProxySettingsScreen needs to observe this directly for UI changes
    // Or if changes are propagated via a method that UI calls after an action
    var errorMessage by mutableStateOf<String?>(null)
    // This is already public and will be observed by the UI

    private val debounceMillis = 300L

    init {
        // 1. Initial Load: Populate the UI from the Store
        viewModelScope.launch {
            val initialDomain = tStore.serverDomainFlow.first()
            val initialSni = tStore.sniFlow.first()
            val initialPort =
                tStore.serverPortFlow.first().let { if (it == 0) "" else it.toString() }
            val initialSecret = tStore.sharedSecretFlow.first()

            _uiServerDomainInput.value = initialDomain
            _uiSniInput.value = initialSni
            _uiServerPortInput.value = initialPort
            _uiSharedSecretInput.value = initialSecret
            _username.value = tStore.usernameFlow.first()
            _password.value = tStore.passwordFlow.first()


            // Sync internal debounced state
            debouncedUiServerDomain = initialDomain
            debouncedUiSni = initialSni
            debouncedUiServerPort = initialPort
            debouncedUiSharedSecret = initialSecret

        }

        // 2. Debounced DOMAIN: Auto-save to Store
        viewModelScope.launch {
            _uiServerDomainInput
                .debounce(debounceMillis)
                .collectLatest { domain ->
                    debouncedUiServerDomain = domain
                    if (validateDomain()) {
                        tStore.saveServerDomain(domain) // Save to Store
                    }
                }
        }

        // Debounced SNI: Auto-save to Store
        viewModelScope.launch {
            _uiSniInput
                .debounce(debounceMillis)
                .collectLatest { sni ->
                    debouncedUiSni = sni
                    if (validateSni()) {
                        tStore.saveSni(sni) // Save to Store
                    }
                }
        }

        // 3. Debounced PORT: Auto-save to Store
        viewModelScope.launch {
            _uiServerPortInput
                .debounce(debounceMillis)
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
                .debounce(debounceMillis)
                .collectLatest { secret ->
                    debouncedUiSharedSecret = secret
                    if (validateSecret()) {
                        tStore.saveSharedSecret(secret) // Save to Store
                    }
                }
        }

        viewModelScope.launch {
            _username
                .debounce { debounceMillis }
                .collectLatest { name ->
                    tStore.saveUsername(name)
                }
        }

        viewModelScope.launch {
            _password
                .debounce { debounceMillis }
                .collectLatest { pass ->
                    tStore.savePassword(pass)
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

    private fun validateSni(): Boolean {
        // Use debouncedUiSni for validation
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

    fun onSniChange(newSni: String) {
        _uiSniInput.value = newSni
    }


    fun onPortChange(newPortString: String) {
        _uiServerPortInput.value = newPortString
    }

    fun onSecretChange(newSecret: String) {
        _uiSharedSecretInput.value = newSecret
    }

    fun onUsernameChange(changed: String) {
        _username.value = changed
    }

    fun onPasswordChange(changed: String) {
        _password.value = changed
    }


    fun saveJwt(jwtContent: String) {
        viewModelScope.launch {
            try {
                tStore.saveJwt(jwtContent)
                Log.d(VM, "JWT saved successfully")
            } catch (e: Exception) {
                errorMessage = "Failed to save JWT: ${e.localizedMessage}"
            }
        }
    }

     fun ok() :Boolean {
        return true
    }

    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }

    /**
     * Triggers a manual token refresh.
     * Returns the new JWT if successful, or null on failure.
     */
    suspend fun refreshTokenNow(): String? {
        return try {
            val repository = SettingsRepoImp(tStore)
            val settingsUseCase = SettingsUseCase(repository)
            val snapshot = settingsUseCase().first()
            
            val tokenManager = TokenManager.getInstance(app)
            tokenManager.refreshNow(snapshot)
        } catch (e: Exception) {
            Log.e(VM, "Manual refresh failed", e)
            null
        }
    }

}