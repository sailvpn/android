package com.illiad.troad.view

import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.illiad.troad.model.Screen
import com.illiad.troad.model.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(viewModel: SettingsViewModel) {
    var passwordVisible by remember { mutableStateOf(false) }

    // Collect the raw input values for the TextFields
    val domainInput by viewModel.uiServerDomainInput.collectAsState()
    val portInput by viewModel.uiServerPortInput.collectAsState()
    val secretInput by viewModel.uiSharedSecretInput.collectAsState()

    // Observe other states like errorMessage and isProxyRunning directly from the ViewModel
    // val currentErrorMessage = viewModel.errorMessage // No need to collect if it's simple State
    // val isProxyCurrentlyRunning = viewModel.isProxyRunning

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Settings") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Main) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
            OutlinedTextField(
                value = domainInput, // Use raw input for display
                onValueChange = { viewModel.onDomainChange(it) },
                label = { Text("Server Domain or IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                // isError is based on the viewModel's errorMessage state
                isError = viewModel.errorMessage?.contains(
                    "Domain",
                    ignoreCase = true
                ) == true || viewModel.errorMessage?.contains(
                    "empty", ignoreCase = true
                ) == true && viewModel.debouncedUiServerDomain.isBlank()

            )

            OutlinedTextField(
                value = portInput, // Use raw input for display
                onValueChange = { viewModel.onPortChange(it) },
                label = { Text("Server Port") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = viewModel.errorMessage?.contains(
                    "Port",
                    ignoreCase = true
                ) == true || viewModel.errorMessage?.contains(
                    "Invalid port",
                    ignoreCase = true
                ) == true
            )

            CryptoSettingsItem(viewModel)

            OutlinedTextField(
                value = secretInput, // Use raw input for display
                onValueChange = { viewModel.onSecretChange(it) },
                label = { Text("Shared Secret") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image =
                        if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) "Hide secret" else "Show secret"
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, description)
                    }
                },
                isError = viewModel.errorMessage?.contains("Secret", ignoreCase = true) == true
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


            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.navigateTo(Screen.Main)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save and Return")
            }
        }
    }
}

@Composable
fun CryptoSettingsItem(viewModel: SettingsViewModel) {
    val currentSelection by viewModel.selectedCrypto.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Authentication Method", style = MaterialTheme.typography.labelMedium)

        // The clickable item showing current selection
        ListItem(
            headlineContent = { Text(currentSelection.value ?: "Select Method") },
            supportingContent = { Text("Choose your preferred encryption") },
            trailingContent = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.clickable { showDialog = true }
        )
    }

    // 1-of-n Selection Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select Authentication") },
            text = {
                Column {
                    viewModel.cryptoOptions.forEach { crypto ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (crypto == currentSelection),
                                    onClick = {
                                        viewModel.onCryptoSelected(crypto)
                                        showDialog = false
                                    }
                                )
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (crypto == currentSelection),
                                onClick = null // Handled by Row selectable
                            )
                            Text(
                                text = crypto.value ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun ProxySettingsScreenPreview() {
    MaterialTheme {
        ProxySettingsScreen(viewModel())
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true)
@Composable
fun ProxySettingsScreenRunningPreview() {
    val context = LocalContext.current
    // Create a dummy Application instance for the preview
    val dummyApplicationForPreview = object : Application() {
        // You might override getApplicationContext() if needed,
        // but often it's not strictly necessary if the ViewModel
        // just needs *an* Application object to satisfy its constructor.
        override fun getApplicationContext(): Context {
            // You could return 'this' or 'context.applicationContext' from the preview
            // Depending on what the ViewModel actually does with it.
            // Returning the preview's application context is often safer.
            return context.applicationContext
        }
    }

    MaterialTheme {
        val app = dummyApplicationForPreview
        val tStore = PreviewTroadStore(context)
        val previewViewModel = ProxySettingsViewModel(app, tStore)
        previewViewModel.isProxyRunning = false
        previewViewModel.uiServerDomain = "proxy.example.com"
        previewViewModel.uiServerPort = "8080"
        previewViewModel.uiSharedSecret = "mysecret" // Add for preview
        ProxySettingsScreen(viewModel = previewViewModel)
    }

}

// Dummy/Preview implementation of SettingsRepository for previews
class PreviewTroadStore(private val context: Context) : TroadStore(context) {
    // Override methods to return dummy data or do nothing for previews
    override val serverDomainFlow: Flow<String> = flowOf("preview.domain.com")
    override val serverPortFlow: Flow<Int> = flowOf(1234)
    override val sharedSecretFlow: Flow<String> = flowOf("previewSecret")
    // ... override other flows and suspend functions as needed for previews

    override suspend fun saveServerDomain(domain: String) { /* No-op for preview */
    }

    override suspend fun saveServerPort(port: Int) { /* No-op for preview */
    }

    override suspend fun saveSharedSecret(secret: String) { /* No-op for preview */
    }
    // ...
}

**/