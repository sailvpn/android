package com.illiad.troad

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.illiad.troad.Consts.ACTION_VPN_STATUS_BROADCAST
import com.illiad.troad.ui.theme.TroadTheme // Your app's theme
import com.illiad.troad.model.SettingsViewModel
import com.illiad.troad.model.SettingsViewModelFactory
import com.illiad.troad.view.ProxySettingsScreen

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vpnPermitRequestLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                // This lambda is called when the activity started by vpnPermissionLauncher.launch() finishes
                if (result.resultCode == Activity.RESULT_OK) {
                    // User granted VPN permission
                    Log.d("VpnPermission", "VPN permission granted by user.")
                    splash()

                } else {
                    // User denied VPN permission or cancelled
                    Log.w(
                        "VpnPermission",
                        "VPN permission denied by user. Result code: ${result.resultCode}"
                    )
                    // Handle denial (e.g., show a message, disable VPN features)
                }
            }

        val prepareIntent = VpnService.prepare(this)

        if (prepareIntent != null) {
            // Permission not yet granted, launch the system dialog
            Log.d("VpnPermission", "Launching system dialog for VPN permission.")
            vpnPermitRequestLauncher.launch(prepareIntent)
        } else {
            // Permission already granted
            Log.d("VpnPermission", "VPN permission was already granted.")
            splash()
        }

    }

    private fun splash() {
        setContent {
            TroadTheme { // Apply your app's theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    ProxySettingsScreen(settingsViewModel)
                }
            }
        }
    }

    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_VPN_STATUS_BROADCAST) {
                val message = intent.getStringExtra(Consts.EXTRA_STATUS_MESSAGE)
                val isConnected = intent.getBooleanExtra(Consts.EXTRA_IS_CONNECTED, false)
                // Update your UI here based on the message and isConnected state
                settingsViewModel.updateVpnStatus(isConnected, message)
                Log.d("MyActivity", "VPN Status Received: $message, Connected: $isConnected")
                // e.g., myStatusTextView.text = message
                // e.g., myConnectButton.isEnabled = !isConnected
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter(ACTION_VPN_STATUS_BROADCAST)
        // If using system-wide sendBroadcast in the service:
        ContextCompat.registerReceiver(
            this, // Context
            vpnStatusReceiver, intentFilter, ContextCompat.RECEIVER_EXPORTED // Specify exported
        )
    }

    override fun onPause() {
        super.onPause()
        // If using system-wide:
        unregisterReceiver(vpnStatusReceiver)
    }

}