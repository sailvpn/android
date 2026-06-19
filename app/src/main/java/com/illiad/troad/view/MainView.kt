package com.illiad.troad.view

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.illiad.troad.model.MainViewModel
import com.illiad.troad.model.Screen
import com.illiad.troad.ui.components.SmartStateSailLogo
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import com.illiad.troad.ui.theme.troadGradients

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(viewModel: MainViewModel) {

    // --- NEW: Global Error Popup (Globo) ---
    // If there are errors in the queue, show the first one
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

    Scaffold(
        containerColor = Color.Transparent, // Allow gradient background to show through
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // Small compact version of your new logo nested inside the center top title bar layout
                    SmartStateSailLogo(
                        status = viewModel.vpnState,
                        modifier = Modifier
                            .size(36.dp) // Scaled down neatly for the Top Bar footprint
                            .clip(CircleShape)
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent // Transparent Top Bar
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color.Transparent, // Transparent Bottom Bar
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
                .background(MaterialTheme.troadGradients.mainBackground)
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Your custom status-aware logo replaces the generic Lock vectors
            SmartStateSailLogo(
                status = viewModel.vpnState,
                modifier = Modifier.size(100.dp) // Generous central focus sizing
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

            Spacer(modifier = Modifier.height(16.dp))

            // VPN Status Text Message Block
            Text(
                text = viewModel.vpnState.statusLabel,
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(80.dp))

            // Large Toggle Execution Node Button
            Button(
                onClick = {
                    if (viewModel.isProxyRunning) viewModel.stopProxyService()
                    else viewModel.startProxyService()
                },
                modifier = Modifier.size(140.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    // Aligns cleanly to Material Theme targets specified inside Theme.kt
                    containerColor = if (viewModel.isProxyRunning) {
                        MaterialTheme.colorScheme.error // Alert State Red
                    } else {
                        MaterialTheme.colorScheme.primary // Wave Blue Base Accent
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

@Composable
fun SpeedMetricsDashboard(viewModel: MainViewModel) {
    val activeState = viewModel.vpnState
    val metrics = viewModel.speedMetrics // Reads the optimized SpeedMetrics object

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(activeState.sailBgColor) // Stable canvas background color
            .padding(16.dp)
    ) {
        Text(text = activeState.statusLabel, style = MaterialTheme.typography.headlineMedium)

        if (viewModel.isProxyRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Short, punchy property queries map cleanly to screen labels
                Text(text = "Down: ${metrics.down}", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Up: ${metrics.up}", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}




