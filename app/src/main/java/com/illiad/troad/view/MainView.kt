package com.illiad.troad.view

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
import androidx.compose.ui.unit.dp
import com.illiad.troad.model.MainViewModel
import com.illiad.troad.model.Screen
// Explicit functional imports for your custom dashboard components
import com.illiad.troad.ui.components.SmartStateSailLogo
import com.illiad.troad.ui.components.VpnStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(viewModel: MainViewModel) {
    // Determine the precise state enum mapping from your core service business logic
    val currentVpnStatus = if (viewModel.isProxyRunning) {
        VpnStatus.CONNECTED
    } else {
        // Fallback state mapping. (You can integrate your connecting states directly here later)
        VpnStatus.DISCONNECTED
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // Small compact version of your new logo nested inside the center top title bar layout
                    SmartStateSailLogo(
                        status = currentVpnStatus,
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
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                TextButton(
                    onClick = { viewModel.navigateTo(Screen.Settings) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Configuration Settings")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Your custom status-aware logo replaces the generic Lock vectors
            SmartStateSailLogo(
                status = currentVpnStatus,
                modifier = Modifier.size(100.dp) // Generous central focus sizing
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



