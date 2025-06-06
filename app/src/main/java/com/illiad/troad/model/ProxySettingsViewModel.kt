package com.illiad.troad.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class ProxySettingsViewModel : ViewModel() {
    var serverDomain by mutableStateOf("")
    var serverPort by mutableStateOf("")
    var sharedSecret by mutableStateOf("") // New field for the secret
    var isProxyRunning by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun onDomainChange(newDomain: String) {
        serverDomain = newDomain
        validateInputs()
    }

    fun onPortChange(newPort: String) {
        if (newPort.all { it.isDigit() } && newPort.length <= 5) {
            serverPort = newPort
            validateInputs()
        }
    }

    fun onSecretChange(newSecret: String) {
        sharedSecret = newSecret
        // You might add validation for the secret here if needed
        // For example, if it cannot be empty when a certain proxy type is selected.
        // For now, we'll assume it can be empty or has no specific format validation.
        validateInputs() // Re-run validation if secret affects button enablement
    }

    private fun validateInputs() {
        // Basic validation - you might want to adjust this based on the secret's requirements
        if (serverDomain.isBlank() || serverPort.isBlank()) {
            errorMessage = "Domain and Port cannot be empty."
        } else if (serverPort.toIntOrNull() == null || serverPort.toInt() !in 1..65535) {
            errorMessage = "Invalid Port number."
        }
        // else if (sharedSecret.isBlank()){ // Example: if secret was mandatory
        //     errorMessage = "Secret cannot be empty."
        // }
        else {
            errorMessage = null
        }
    }

    fun startProxyService() {
        validateInputs()
        if (errorMessage == null) {
            println("Attempting to start proxy with Domain: $serverDomain, Port: $serverPort, Secret: $sharedSecret")
            isProxyRunning = true
        }
    }

    fun stopProxyService() {
        println("Stopping proxy service")
        isProxyRunning = false
    }
}