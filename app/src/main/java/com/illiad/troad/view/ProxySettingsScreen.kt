package com.illiad.troad.view

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.illiad.troad.model.ProxySettingsViewModel
import com.illiad.troad.model.TroadStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(viewModel: ProxySettingsViewModel = viewModel()) {
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Proxy Settings") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Configure Remote Proxy Server",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = viewModel.uiServerDomain,
                onValueChange = { viewModel.onDomainChange(it) },
                label = { Text("Server Domain or IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = viewModel.errorMessage?.contains("Domain") == true
            )

            OutlinedTextField(
                value = viewModel.uiServerPort,
                onValueChange = { viewModel.onPortChange(it) },
                label = { Text("Server Port") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = viewModel.errorMessage?.contains("Port") == true
            )

            // New Secret Field
            OutlinedTextField(
                value = viewModel.uiSharedSecret,
                onValueChange = { viewModel.onSecretChange(it) },
                label = { Text("Shared Secret (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff

                    // Localized description for accessibility services
                    val description = if (passwordVisible) "Hide secret" else "Show secret"

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, description)
                    }
                }
                // isError = viewModel.errorMessage?.contains("Secret") == true // If you add validation for secret
            )

            if (viewModel.errorMessage != null) {
                Text(
                    text = viewModel.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (viewModel.isProxyRunning) {
                Button(
                    onClick = { viewModel.stopProxyService() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop Proxy Service")
                }
                Text(
                    "Proxy Status: Running",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

            } else {
                Button(
                    onClick = { viewModel.startProxyService() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewModel.errorMessage == null && viewModel.uiServerDomain.isNotBlank() && viewModel.uiServerPort.isNotBlank()
                ) {
                    Text("Start Proxy Service")
                }
                Text(
                    "Proxy Status: Stopped",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun ProxySettingsScreenPreview() {
    MaterialTheme {
        ProxySettingsScreen()
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun ProxySettingsScreenRunningPreview() {
    MaterialTheme {
        val previewViewModel = ProxySettingsViewModel()
        previewViewModel.isProxyRunning = false
        previewViewModel.uiServerDomain = "proxy.example.com"
        previewViewModel.uiServerPort = "8080"
        previewViewModel.uiSharedSecret = "mysecret" // Add for preview
        ProxySettingsScreen(viewModel = previewViewModel)
    }
}