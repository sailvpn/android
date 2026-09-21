package com.illiad.troad.view

import android.widget.Toast
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
import com.illiad.troad.model.SettingsViewModel
import com.illiad.troad.service.security.Cryptos
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

import com.illiad.troad.ui.theme.troadGradients
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    viewModel: SettingsViewModel,
    mainViewModel: com.illiad.troad.model.MainViewModel,
    onBack: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    var showAcqJWTDialog by remember { mutableStateOf(false) }
    var showJwtDialog by remember { mutableStateOf(false) }

    // Stream State Collection from view model architecture layer
    val domainInput by viewModel.uiServerDomainInput.collectAsState()
    val sniInput by viewModel.uiSniInput.collectAsState()
    val portInput by viewModel.uiServerPortInput.collectAsState()
    val secretInput by viewModel.uiSharedSecretInput.collectAsState()
    val cryptoSelected by viewModel.selectedCrypto.collectAsState()
    val renewSelection by viewModel.autoRenew.collectAsState()

    if (showAcqJWTDialog) {
        AcquireTokenDialog(
            onDismiss = { showAcqJWTDialog = false },
            viewModel = viewModel
        )
    }

    if (showJwtDialog) {
        ConfigJwtDialog(
            onDismiss = { showJwtDialog = false },
            viewModel = viewModel
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Server Settings")
                        if (mainViewModel.cryptoStatus.label.isNotBlank()) {
                            val statusColor = mainViewModel.cryptoStatus.color
                            Text(
                                text = mainViewModel.cryptoStatus.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (statusColor != Color.Unspecified) Color.White else MaterialTheme.colorScheme.tertiary,
                                modifier = if (statusColor != Color.Unspecified) {
                                    Modifier
                                        .padding(top = 2.dp)
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(statusColor.copy(alpha = 0.8f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                } else Modifier
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // Utilizes the correct stable localized layout mirror vector class reference
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate backward to main panel"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,     // Makes TopBar background transparent
                    scrolledContainerColor = Color.Transparent // Remains transparent when scrolling
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.troadGradients.mainBackground)
                .padding(paddingValues)
                .padding(16.dp)
                // Enable safe vertical touch scrolling over smaller phone display dimensions
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SERVER CORE NETWORK FIELDS GROUP
            OutlinedTextField(
                value = domainInput,
                onValueChange = { viewModel.onDomainChange(it) },
                label = { Text("Server Domain or IP") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = viewModel.errorMessage?.contains("Domain", ignoreCase = true) == true

            )

            OutlinedTextField(
                value = sniInput,
                onValueChange = { viewModel.onSniChange(it) },
                label = { Text("Server Name Identifier (SNI)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = viewModel.errorMessage?.contains("SNI", ignoreCase = true) == true
            )

            OutlinedTextField(
                value = portInput,
                onValueChange = { viewModel.onPortChange(it) },
                label = { Text("Server Port") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = viewModel.errorMessage?.contains("Port", ignoreCase = true) == true
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // DYNAMIC ALGORITHM SELECTION PANEL
            CryptoSettingsItem(viewModel)

            // OPTION CONDITIONAL A: SHA-256 Shared Secret Entry Layout
            if (cryptoSelected == Cryptos.SHA_256) {
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { viewModel.onSecretChange(it) },
                    label = { Text("Shared Secret") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = "Toggle secret concealment string visibility")
                        }
                    },
                    isError = viewModel.errorMessage?.contains("Secret", ignoreCase = true) == true
                )
            }

            // GLOBAL FORM INPUT ERROR NOTIFICATION BANNER
            viewModel.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // OPTION CONDITIONAL B: JWT Token Credentials Retrieval Flow
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

            // OPTION CONDITIONAL C: Local Disk JWT Profile Selection File Parsing Flow
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

            // OPTION CONDITIONAL D: JWT Automatic Validity Recurrence Profile Toggles
            if (cryptoSelected == Cryptos.JWT) {
                TokenRenewDropdown(viewModel)
            }

            // FIX: Replaced Modifier.weight(1f) to prevent crash inside a verticalScroll container
            Spacer(modifier = Modifier.height(32.dp))

            // PRIMARY SYSTEM PANEL RETURN ACTION NODE BUTTON
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
        // Section categorization title
        Text(
            text = "Authentication Method",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary // Leverages your signature Wave Blue token
        )

        // Interactive settings list item showing active choice configuration state
        ListItem(
            headlineContent = { Text(currentSelection.value) },
            supportingContent = { Text("Choose your preferred encryption algorithm") },
            trailingContent = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand encryption options") },
            modifier = Modifier.clickable { showDialog = true }
        )
    }

    // 1-of-N Modal Selection Sheet Dialog Box Overlay
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select Authentication") },
            text = {
                Column {
                    viewModel.cryptoOptions.forEach { crypto ->
                        val isSelected = (crypto == currentSelection)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.onCryptoSelected(crypto)
                                        showDialog = false // Instantly dismiss overlay sheet layout panel
                                    }
                                )
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Native M3 radio selector element matching your Coral/Blue styling choices
                            RadioButton(
                                selected = isSelected,
                                onClick = null, // Handled by Row container layer selection scopes
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.outline
                                )
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
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ConfigJwtDialog(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Configuration file launcher utilizing Android Storage Access Framework contracts
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { selectedUri ->
                try {
                    // Safe I/O operation processing text characters via content resolvers
                    val content = context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        input.bufferedReader().use { it.readText() }
                    }

                    content?.let { jwtString ->
                        scope.launch {
                            viewModel.saveJwt(jwtString)
                            Toast.makeText(context, "JWT was loaded successfully", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                } catch (e: Exception) {
                    // Fail-safe handler catching corrupted files or storage read denial blocks
                    Toast.makeText(context, "Failed to parse JWT configuration file", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Config JWT") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Import your JSON Web Token security authorization profiles straight from your local device disk directories.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )

                // Full-width interactive action selector button
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // Launches document picker matching standard universal fallback mime-type strings
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }
                ) {
                    Text("Select JWT File")
                }
            }
        },
        confirmButton = {
            // Your dialog relies on file selection for execution, so Cancel rests inside the confirmation slot
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
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
    // FIX: Safely collect the StateFlow at the top level of the composable
    val currentUsername by viewModel.username.collectAsState()
    val currentPassword by viewModel.password.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Acquire JWT Token") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Username Input Node Layer
                OutlinedTextField(
                    value = currentUsername, // Reading clean top-level collected state string
                    onValueChange = { viewModel.onUsernameChange(it) },
                    label = { Text("Username") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Password Input Node Layer with Toggle Masking
                OutlinedTextField(
                    value = currentPassword, // Reading clean top-level collected state string
                    onValueChange = { viewModel.onPasswordChange(it) },
                    label = { Text("Password") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = "Toggle password visibility")
                        }
                    },
                    singleLine = true
                )

                // Embedded Validity Period Dropdown Selection Layer
                DurationDropdown(viewModel)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        // Triggers the authentication thread over network tunnel hooks safely
                        val success = viewModel.acquireJwt()
                        if (success) onDismiss()
                    }
                }
            ) {
                Text("Acquire")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationDropdown(viewModel: SettingsViewModel) {
    val currentDuration by viewModel.duration.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        // Updated text field container to eliminate legacy layout warning indicators
        OutlinedTextField(
            value = currentDuration.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Validity Period") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            // Seamless alignment into your custom Wave Blue theme color palette tokens
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryEditable, enabled = true) // Stable modern layout hook
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Iterates through your operational duration entries data model list seamlessly
            Duration.entries.forEach { duration ->
                DropdownMenuItem(
                    text = { Text(duration.label) },
                    onClick = {
                        viewModel.onDurationChanged(duration)
                        expanded = false // Instantly dismiss overlay sheet layout panel
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenRenewDropdown(viewModel: SettingsViewModel) {
    // Correctly collect the StateFlow array stream from your settings state view model
    val currentRenew by viewModel.autoRenew.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // In Material 3, Modifier.menuAnchor() must be called directly inside the Box scope container
        OutlinedTextField(
            value = currentRenew.name, // Displays the active string configuration option
            onValueChange = {},
            readOnly = true,
            label = { Text("Token Auto Renew") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            // Uses current standard M3 layout color overrides mapping to your theme structure
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryEditable, enabled = true) // Stable modern layout hook
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
                        expanded = false // Instantly dismiss overlay sheet layout panel
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
