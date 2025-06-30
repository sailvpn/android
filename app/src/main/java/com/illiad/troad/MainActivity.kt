package com.illiad.troad

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.illiad.troad.ui.theme.TroadTheme // Your app's theme
import com.illiad.troad.model.ProxySettingsViewModel
import com.illiad.troad.model.ProxySettingsViewModelFactory
import com.illiad.troad.view.ProxySettingsScreen

class MainActivity : ComponentActivity() {

    private val proxySettingsViewModel: ProxySettingsViewModel by viewModels {
        ProxySettingsViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TroadTheme { // Apply your app's theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    ProxySettingsScreen(proxySettingsViewModel)
                }
            }
        }
    }

    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Consts.ACTION_VPN_STATUS_BROADCAST) {
                val message = intent.getStringExtra(Consts.EXTRA_STATUS_MESSAGE)
                val isConnected = intent.getBooleanExtra(Consts.EXTRA_IS_CONNECTED, false)

                // Update your UI here based on the message and isConnected state
                Log.d("MyActivity", "VPN Status Received: $message, Connected: $isConnected")
                // e.g., myStatusTextView.text = message
                // e.g., myConnectButton.isEnabled = !isConnected
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter(Consts.ACTION_VPN_STATUS_BROADCAST)
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