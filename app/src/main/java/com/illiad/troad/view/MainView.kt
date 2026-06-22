package com.illiad.troad.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.illiad.troad.model.MainViewModel
import com.illiad.troad.model.Screen
import com.illiad.troad.ui.components.SmartStateSailLogo
import com.illiad.troad.ui.theme.troadGradients

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(viewModel: MainViewModel) {

    // --- Global Error Popup Queue Observer ---
    if (viewModel.errorQueue.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Connection Alert") },
            text = { Text(viewModel.errorQueue.first()) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("Dismiss")
                }
            }
        )
    }

    // Outer Box ensures a seamless full-screen background gradient across all system bars
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.troadGradients.mainBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent, // Allows the full Box gradient to show through perfectly
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        SmartStateSailLogo(
                            status = viewModel.vpnState,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                BottomAppBar(
                    containerColor = Color.Transparent,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    TextButton(
                        onClick = { viewModel.navigateTo(Screen.Settings) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Config",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween // Balanced vertical distribution
            ) {
                // Central Dashboard Information Cluster
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SmartStateSailLogo(
                        status = viewModel.vpnState,
                        modifier = Modifier.size(100.dp)
                    )

                    Text(
                        text = "Sail",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )

                    Text(
                        text = "harness the web",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = viewModel.vpnState.statusLabel,
                        style = MaterialTheme.typography.headlineMedium
                    )

                    // [SPEED DASHBOARD INJECTION SLOT]: Unwired until data acquisition is ready
                }

                // Central Execution Trigger Button
                Box(
                    modifier = Modifier.padding(bottom = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            if (viewModel.isProxyRunning) viewModel.stopProxyService()
                            else viewModel.startProxyService()
                        },
                        modifier = Modifier.size(140.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.isProxyRunning) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Text(
                            text = if (viewModel.isProxyRunning) "STOP" else "START",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}


