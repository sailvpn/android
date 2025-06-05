package com.illiad.troad.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.illiad.troad.model.ProxySettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(viewModel: ProxySettingsViewModel = viewModel()) {
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
                value = viewModel.serverDomain,
                onValueChange = { viewModel.onDomainChange(it) },
                label = { Text("Server Domain or IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = viewModel.errorMessage?.contains("Domain") == true
            )

            OutlinedTextField(
                value = viewModel.serverPort,
                onValueChange = { viewModel.onPortChange(it) },
                label = { Text("Server Port") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = viewModel.errorMessage?.contains("Port") == true
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
                    enabled = viewModel.errorMessage == null && viewModel.serverDomain.isNotBlank() && viewModel.serverPort.isNotBlank()
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

@Preview(showBackground = true)
@Composable
fun ProxySettingsScreenPreview() {
    MaterialTheme { // Ensure a MaterialTheme is applied for preview
        ProxySettingsScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun ProxySettingsScreenRunningPreview() {
    MaterialTheme {
        val previewViewModel = ProxySettingsViewModel()
        previewViewModel.isProxyRunning = true
        previewViewModel.serverDomain = "proxy.example.com"
        previewViewModel.serverPort = "8080"
        ProxySettingsScreen(viewModel = previewViewModel)
    }
}
