package com.illiad.troad.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.illiad.troad.R
import com.illiad.troad.model.MainViewModel
import com.illiad.troad.model.Screen
import com.illiad.troad.model.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(viewModel: MainViewModel) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // Logo centered in the Top Bar
                    Image(
                        painter = painterResource(id = R.drawable.troy), // Or R.mipmap.ic_launcher_round if using adaptive icon's round version
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(48.dp) // Adjust size as needed
                            .clip(CircleShape) // Clip the image to a circle
                            .border( // Add a border
                                width = 2.dp, // Border width
                                color = MaterialTheme.colorScheme.primary, // Border color (adjust as needed)
                                shape = CircleShape // Ensure border shape matches clip shape
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
                // Navigation button in the Bottom Bar
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
        // Center part displaying VPN status and Start/Stop toggle
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // Crucial: prevents content from being hidden by bars
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // VPN Status Icon
            Icon(
                imageVector = if (viewModel.isProxyRunning) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = if (viewModel.isProxyRunning) MaterialTheme.colorScheme.primary else Color.Gray
            )

            // VPN Status Message
            Text(
                text = viewModel.vpnStatusMessage,
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Large Toggle Button
            Button(
                onClick = {
                    if (viewModel.isProxyRunning) viewModel.stopProxyService()
                    else viewModel.startProxyService()
                },
                modifier = Modifier.size(150.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.isProxyRunning)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (viewModel.isProxyRunning) "STOP" else "START")
            }
        }
    }
}



