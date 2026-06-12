package com.illiad.troad

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
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.illiad.troad.Consts.ACTION_VPN_STATUS_BROADCAST
import com.illiad.troad.model.Screen
import com.illiad.troad.ui.theme.TroadTheme // Your app's theme
import com.illiad.troad.model.SettingsViewModel
import com.illiad.troad.model.MainViewModel
import com.illiad.troad.model.SettingsViewModelFactory
import com.illiad.troad.view.MainView
import com.illiad.troad.view.SettingsView

class MainActivity : ComponentActivity() {

    // 1. Lightweight ViewModel: Loads instantly with 0 disk I/O in init
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vpnPermitRequestLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    Log.d("VpnPermission", "VPN permission granted.")
                    splash()
                } else {
                    Log.w("VpnPermission", "VPN permission denied.")
                }
            }

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermitRequestLauncher.launch(prepareIntent)
        } else {
            splash()
        }
    }

    private fun splash() {
        setContent {
            TroadTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Use Crossfade for a smooth, lazy transition
                    Crossfade(targetState = mainViewModel.currentScreen) { screen ->
                        when (screen) {
                            Screen.Main -> MainView(mainViewModel)
                            Screen.Settings -> {
                                // 2. Instantiates SettingsViewModel ONLY when the user clicks 'Settings'
                                val settingsViewModel: SettingsViewModel = viewModel(
                                    factory = SettingsViewModelFactory(application)
                                )
                                SettingsView(
                                    viewModel = settingsViewModel,
                                    onBack = { mainViewModel.navigateTo(Screen.Main) })
                            }
                        }
                    }
                }
            }
        }
    }

    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_VPN_STATUS_BROADCAST) {
                val message = intent.getStringExtra(Consts.EXTRA_STATUS_MESSAGE)
                val isConnected = intent.getBooleanExtra(Consts.EXTRA_IS_CONNECTED, false)

                // 3. Update the lightweight ViewModel instead of the heavy one
                mainViewModel.updateVpnStatus(isConnected, message)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter(ACTION_VPN_STATUS_BROADCAST)
        ContextCompat.registerReceiver(
            this, vpnStatusReceiver, intentFilter, ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(vpnStatusReceiver)
    }
}