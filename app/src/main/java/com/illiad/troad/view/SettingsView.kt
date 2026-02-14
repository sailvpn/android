package com.illiad.troad.view

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.unit.dp
import com.illiad.troad.model.Duration
import com.illiad.troad.model.Screen
import com.illiad.troad.model.SettingsViewModel
import com.illiad.troad.service.security.Cryptos
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(viewModel: SettingsViewModel) {
    var passwordVisible by remember { mutableStateOf(false) }

    // State for managing popups
    var showCaCertDialog by remember { mutableStateOf(false) }
    var showAcqJWTDialog by remember { mutableStateOf(false) }
    var showJwtDialog by remember { mutableStateOf(false) }

    val domainInput by viewModel.uiServerDomainInput.collectAsState()
    val portInput by viewModel.uiServerPortInput.collectAsState()
    val secretInput by viewModel.uiSharedSecretInput.collectAsState()

    val cryptoSelected by viewModel.selectedCrypto.collectAsState()
    // State for Enum-based Dropdown
    val renewSelection by viewModel.autoRenew.collectAsState()
    if (showCaCertDialog) {
        ConfigCaCertDialog(
            onDismiss = { showCaCertDialog = false },
            viewModel = viewModel
        )
    }

    if(showAcqJWTDialog) {
        AcquireTokenDialog(
            onDismiss = { showAcqJWTDialog = false },
            viewModel = viewModel,
            modifier = Modifier
        )
    }

    if (showJwtDialog) {
        ConfigJwtDialog(
            onDismiss = { showJwtDialog = false },
            viewModel = viewModel
        )
    }

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
                .fillMaxSize()
                .verticalScroll(rememberScrollState()), // Added scroll for small screens
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Existing TextFields
            OutlinedTextField(
                value = domainInput,
                onValueChange = { viewModel.onDomainChange(it) },
                label = { Text("Server Domain or IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = viewModel.errorMessage?.contains("Domain", ignoreCase = true) == true
            )

            OutlinedTextField(
                value = portInput,
                onValueChange = { viewModel.onPortChange(it) },
                label = { Text("Server Port") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = viewModel.errorMessage?.contains("Port", ignoreCase = true) == true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { showCaCertDialog = true }
                ) {
                    Text("Config CA Cert")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            CryptoSettingsItem(viewModel)

            if (cryptoSelected == Cryptos.SHA_256) {
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { viewModel.onSecretChange(it) },
                    label = { Text("Shared Secret") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image =
                            if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null)
                        }
                    },
                    isError = viewModel.errorMessage?.contains("Secret", ignoreCase = true) == true
                )
            }

            if (viewModel.errorMessage != null) {
                Text(
                    text = viewModel.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if(cryptoSelected == Cryptos.JWT) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showAcqJWTDialog = true }
                    ) {
                        Text("Acquire JWT by Username/Password")
                    }
                }
            }

            if (cryptoSelected == Cryptos.JWT2 || cryptoSelected == Cryptos.JWT) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showJwtDialog = true }
                    ) {
                        Text("Config JWT")
                    }
                }
            }
            if (cryptoSelected == Cryptos.JWT) {
                TokenRenewDropdown(viewModel)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.navigateTo(Screen.Main) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Return")
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
            headlineContent = { Text(currentSelection.value) },
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
                                text = crypto.value,
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

@Composable
fun ConfigCaCertDialog(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel // Pass ViewModel to handle saving
) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("No file selected") }
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { selectedUri ->
                // 1. Read the file content as String
                val content = context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    input.bufferedReader().use { it.readText() }
                }

                // 2. Save to DataStore via ViewModel
                content?.let { certString ->
                    fileName = selectedUri.lastPathSegment ?: "Certificate Loaded"
                    scope.launch {
                        viewModel.saveCaCert(certString)
                    }
                }
            }
        }
    )

    // Define the specific MIME types for certificates
    val certMimeTypes = arrayOf(
        "application/x-x509-ca-cert", // .crt, .der, .cer
        "application/x-pem-file",     // .pem
        "application/pkix-cert",      // Public Key Infrastructure
        "application/x-pkcs12"        // .p12, .pfx (if you support bundles)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Config CA Cert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Selected: $fileName")
                Button(onClick = { filePickerLauncher.launch(certMimeTypes) }) {
                    Text("Choose Certificate File")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConfigJwtDialog(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel // Use the ViewModel!
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileName by remember { mutableStateOf("No file selected") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            // Reading file in a background-friendly way
            val content = context.contentResolver.openInputStream(selectedUri)?.use { input ->
                input.bufferedReader().use { it.readText() }
            }

            content?.let { jwtString ->
                fileName = "Loaded JWT"
                // Persist it immediately so it's not lost on rotation
                scope.launch {
                    viewModel.saveJwt(jwtString)
                }
            }
        }
    }

    // Define the specific MIME types for certificates
    val certMimeTypes = arrayOf(
        "application/x-x509-ca-cert", // .crt, .der, .cer
        "application/x-pem-file",     // .pem
        "application/pkix-cert",      // Public Key Infrastructure
        "application/x-pkcs12"        // .p12, .pfx (if you support bundles)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Config JWT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current status: $fileName")
                Button(onClick = { filePickerLauncher.launch(certMimeTypes) }) {
                    // Using "OpenDocument" is generally more reliable on modern Android
                    Text("Select JWT File")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcquireTokenDialog(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf(viewModel.username) }
    var password by remember { mutableStateOf(viewModel.password) }
    var passwordVisible by remember { mutableStateOf(false) }

    // State for Enum-based Dropdown
    var expanded by remember { mutableStateOf(false) }
    var selectedDuration by remember { mutableStateOf(Duration.DEFAULT) }

    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acquire JWT Token") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = username.collectAsState().value,
                    onValueChange = { viewModel.onUsernameChange(it) },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password.collectAsState().value,
                    onValueChange = { viewModel.onPasswordChange(it) },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image =
                            if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null)
                        }
                    },
                    singleLine = true
                )

                // 1-of-N Validity Period using Duration Enum
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedDuration.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Validity Period") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Duration.entries.forEach { duration ->
                            DropdownMenuItem(
                                text = { Text(duration.label) },
                                onClick = {
                                    selectedDuration = duration
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        // only duration is pass back, pther values are already stored back in viewModel
                        val success = viewModel.acquireJwt(
                            selectedDuration.minutes
                        )
                        if (success) onDismiss()
                    }
                }
            ) {
                Text("Acquire")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenRenewDropdown(viewModel: SettingsViewModel) {
    val currentRenew by viewModel.autoRenew.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = currentRenew.name, // Display the current enum name
            onValueChange = {},
            readOnly = true,
            label = { Text("Token Auto Renew") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            viewModel.autoRenewOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        viewModel.onAutoRenewalChanged(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }

}