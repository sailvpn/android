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
fun SettingsView(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    // State for managing popups
    var showCaCertDialog by remember { mutableStateOf(false) }
    var showAcqJWTDialog by remember { mutableStateOf(false) }
    var showJwtDialog by remember { mutableStateOf(false) }

    val domainInput by viewModel.uiServerDomainInput.collectAsState()
    val sniInput by viewModel.uiSniInput.collectAsState()
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

    if (showAcqJWTDialog) {
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
                    IconButton(onClick = onBack) {
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
                value = sniInput,
                onValueChange = { viewModel.onSniChange(it) },
                label = { Text("Server Name Identifier") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = viewModel.errorMessage?.contains("SNI", ignoreCase = true) == true
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

            if (cryptoSelected == Cryptos.JWT) {
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
                onClick = onBack,
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
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { selectedUri ->
                // 1. Read the file content
                val content = context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    input.bufferedReader().use { it.readText() }
                }

                // 2. Save, Notify, and then Close the Dialog
                content?.let { certString ->
                    scope.launch {
                        viewModel.saveCaCert(certString)

                        // Show the prompt (Toast)
                        android.widget.Toast.makeText(
                            context,
                            "CA Cert was loaded",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()

                        // Close the dialog to return to the menu
                        onDismiss()
                    }
                }
            }
        }
    )

    val certMimeTypes = arrayOf(
        "application/x-x509-ca-cert",
        "application/x-pem-file",
        "application/pkix-cert",
        "application/x-pkcs12"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Config CA Cert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select your certificate file to update the server configuration.")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { filePickerLauncher.launch(certMimeTypes) }
                ) {
                    Text("Choose Certificate File")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConfigJwtDialog(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { selectedUri ->
                val content = context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    input.bufferedReader().use { it.readText() }
                }

                content?.let { jwtString ->
                    scope.launch {
                        viewModel.saveJwt(jwtString)
                        android.widget.Toast.makeText(context, "JWT was loaded", android.widget.Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            }
        }
    )

    // Broaden MIME types to ensure the system finds compatible apps/files
    val jwtMimeTypes = arrayOf("*/*") // Use "*/*" to allow all files if specific types fail

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Config JWT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Ensure the button is easily clickable by filling the width
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { filePickerLauncher.launch(jwtMimeTypes) }
                ) {
                    Text("Select JWT File")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
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
                DurationDropdown(viewModel)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        // only duration is pass back, pther values are already stored back in viewModel
                        val success = viewModel.acquireJwt(
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
fun DurationDropdown(viewModel: SettingsViewModel) {
    val currentDuration by viewModel.duration.collectAsState()
    var expanded by remember { mutableStateOf(false) }

// 1-of-N Validity Period using Duration Enum
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = currentDuration.label,
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
                        viewModel.onDurationChanged(duration)
                        expanded = false
                    }
                )
            }
        }
    }
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
                        viewModel.onAutoRenewChanged(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }

}