package com.illiad.troad.model

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProxySettingsViewModel(
    app: Application,
    private val tStore: TroadStore, // Inject or instantiate
) : AndroidViewModel(app) {

    // --- State for UI TextFields (what the user is currently typing) ---
    var uiServerDomain by mutableStateOf("")
    var uiServerPort by mutableStateOf("") // Keep as String for flexible input
    var uiSharedSecret by mutableStateOf("")

    // --- StateFlows from DataStore (the persisted values) ---
    // These are what you'd typically use if you want parts of your UI
    // to always reflect the *saved* state, or for other logic.
    val tStoreServerDomain =
        tStore.serverDomainFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val tStoreServerPort = tStore.serverPortFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        1080
    ) // Default port
    val tStoreSharedSecret =
        tStore.sharedSecretFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    var isProxyRunning by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    init {
        // Load initial values from DataStore into UI fields
        viewModelScope.launch {
            uiServerDomain = tStoreServerDomain.first() // Get the first emitted (current) value
            uiServerPort = tStoreServerPort.first()
                .let { if (it == 0) "" else it.toString() } // Handle default/empty
            uiSharedSecret = tStoreSharedSecret.first()
            validateInputs() // Validate loaded data
        }
    }

    // --- Validation (Operates on UI input states) ---
    private fun validateInputs(): Boolean {
        if (uiServerDomain.isBlank()) {
            errorMessage = "Server domain cannot be empty."
            return false
        }

        if (uiServerPort.isBlank()) {
            errorMessage = "Server port cannot be empty."
            return false
        }

        val port = uiServerPort.toIntOrNull()
        if (port == null || port !in 1..65535) {
            errorMessage = "Invalid port number. Must be between 1 and 65535."
            return false
        }

        // Add other validations as needed (e.g., for uiSharedSecret)

        errorMessage = null
        return true
    }

    // --- UI Event Handlers ---
    fun onDomainChange(newDomain: String) {
        uiServerDomain = newDomain
        validateInputs()
        // Optional: Save on-the-fly if desired, but often better to save on explicit action
        // if (validateInputs()) {
        //     viewModelScope.launch { tStore.saveServerDomain(uiServerDomain) }
        // }
    }

    fun onPortChange(newPortString: String) {
        uiServerPort = newPortString
        validateInputs()
        // Optional: Save on-the-fly
        // if (validateInputs()) {
        //     uiServerPort.toIntOrNull()?.let { portInt ->
        //         viewModelScope.launch { tStore.saveServerPort(portInt) }
        //     }
        // }
    }

    fun onSecretChange(newSecret: String) {
        uiSharedSecret = newSecret
        validateInputs()
        // Optional: Save on-the-fly
        // if (validateInputs()) {
        //     viewModelScope.launch { tStore.saveSharedSecret(uiSharedSecret) }
        // }
    }

    // --- Actions ---
    fun startProxyService() {
        if (validateInputs()) { // Crucial check before trying to save and start
            viewModelScope.launch {
                // Save the validated UI inputs to DataStore
                tStore.saveServerDomain(uiServerDomain)
                uiServerPort.toIntOrNull()
                    ?.let { portInt -> // Should be valid due to validateInputs()
                        tStore.saveServerPort(portInt)
                    }
                tStore.saveSharedSecret(uiSharedSecret)

                // Now that settings are saved, proceed to start the service
                // Use the persisted values (or the just-saved UI values if you prefer, they should match)
                println(
                    "Attempting to start proxy with Domain: ${tStoreServerDomain.value}, " + "Port: ${tStoreServerPort.value}, Secret: ${tStoreSharedSecret.value}"
                )
                // Actual logic to start your VPN service using the validated and saved values
                isProxyRunning = true
            }
        }
        // If validateInputs() is false, errorMessage is already set for the UI
    }

    fun stopProxyService() {
        println("Stopping proxy service")
        isProxyRunning = false
    }
}