package com.illiad.troad.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class ProxySettingsViewModel : ViewModel() {
    var serverDomain by mutableStateOf("")
    var serverPort by mutableStateOf("")
    var isProxyRunning by mutableStateOf(false) // To reflect service state
    var errorMessage by mutableStateOf<String?>(null)

    fun onDomainChange(newDomain: String) {
        serverDomain = newDomain
        validateInputs()
    }

    fun onPortChange(newPort: String) {
        // Allow only digits and limit length for a typical port
        if (newPort.all { it.isDigit() } && newPort.length <= 5) {
            serverPort = newPort
            validateInputs()
        }
    }

    private fun validateInputs() {
        if (serverDomain.isBlank() || serverPort.isBlank()) {
            errorMessage = "Domain and Port cannot be empty."
        } else if (serverPort.toIntOrNull() == null || serverPort.toInt() !in 1..65535) {
            errorMessage = "Invalid Port number."
        } else {
            errorMessage = null
        }
    }

    fun startProxyService() {
        validateInputs()
        if (errorMessage == null) {
            // TODO: This is where you would actually start your Android Service
            // For now, we'll just toggle the state and simulate
            println("Attempting to start proxy with Domain: $serverDomain, Port: $serverPort")
            isProxyRunning = true // In a real app, this would be updated by the Service
        }
    }

    fun stopProxyService() {
        // TODO: This is where you would stop your Android Service
        println("Stopping proxy service")
        isProxyRunning = false // In a real app, this would be updated by the Service
    }
}