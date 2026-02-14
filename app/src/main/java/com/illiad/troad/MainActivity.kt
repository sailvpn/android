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
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.illiad.troad.Consts.ACTION_VPN_STATUS_BROADCAST
import com.illiad.troad.Consts.CM
import com.illiad.troad.model.AutoRenew
import com.illiad.troad.model.Duration
import com.illiad.troad.model.Screen
import com.illiad.troad.ui.theme.TroadTheme // Your app's theme
import com.illiad.troad.model.SettingsViewModel
import com.illiad.troad.model.SettingsViewModelFactory
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.security.CertManager.dtlsCtx
import com.illiad.troad.service.security.CertManager.sslCtx
import com.illiad.troad.service.security.CertManager.trustManagers
import com.illiad.troad.service.security.Cryptos
import com.illiad.troad.service.security.client.TokenManager
import com.illiad.troad.view.MainView
import com.illiad.troad.view.SettingsView
import io.netty.handler.ssl.SslContextBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(application)
    }

    // CoroutineScope for launching background tasks, using an IO dispatcher for network and file operations.
    // SupervisorJob for managing coroutines within the service, allowing child coroutines to fail without canceling the entire scope.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tStore by lazy { TroadStore(applicationContext) }
    private var settingsObserver: Job? = null
    private var certManagerObserver: Job ? = null
    private var tokenManager: TokenManager? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Connect the observer
        val observer = MainActivityObserver(
            onStartup = {
                startSettingsObserver()
                startCertManagerObserver()
                tokenManager = TokenManager.getInstance(applicationContext)
                tokenManager?.initialize()
            },
            onCleanup = {
                serviceScope.cancel()
                settingsObserver?.cancel()
                certManagerObserver?.cancel()
                tokenManager?.shutdown()
            })

        lifecycle.addObserver(observer)

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
            TroadTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (settingsViewModel.currentScreen) {
                        Screen.Main -> MainView(settingsViewModel)
                        Screen.Settings -> SettingsView(settingsViewModel)
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
                // Update your UI here based on the message and isConnected state
                settingsViewModel.updateVpnStatus(isConnected, message)
                Log.d("MyActivity", "VPN Status Received: $message, Connected: $isConnected")
                // e.g., myStatusTextView.text = message
                // e.g., myConnectButton.isEnabled = !isConnected
            }
        }
    }

    private fun startSettingsObserver() {
        settingsObserver?.cancel()
        settingsObserver = serviceScope.launch {
            // Combine all flows into a single configuration stream
            combine(
                tStore.serverDomainFlow,
                tStore.serverPortFlow,
                tStore.caCertFlow,
                tStore.selectedCryptoFlow,
                tStore.sharedSecretFlow,
                tStore.jwtFlow,
                tStore.usernameFlow,
                tStore.passwordFlow,
                tStore.durationFlow,
                tStore.autoRenewFlow
            ) { v ->
                // This data class acts as a snapshot of your current settings
                Settings(
                    v[0] as String,
                    v[1] as Int,
                    v[2] as String,
                    v[3] as Cryptos,
                    v[4] as String,
                    v[5] as String,
                    v[6] as String,
                    v[7] as String,
                    v[8] as Duration,
                    v[9] as AutoRenew
                )
            }.collectLatest { settings ->
                // This block runs whenever ANY of the 6 settings change
                Utils.settings = settings
            }
        }
    }

    private fun startCertManagerObserver() {
        certManagerObserver?.cancel()
        certManagerObserver = serviceScope.launch {
            Utils.settingState
                .map { settings ->
                    {
                        synchronized(this) {
                            val ca =
                                ByteArrayInputStream(settings?.cacert?.toByteArray(StandardCharsets.UTF_8))
                            try {

                                // 1. Prepare empty Client Identity (only required for Client Auth)
                                val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                                    load(
                                        null,
                                        null
                                    ) // Passing null for stream and password creates an empty in-memory store
                                }

                                val kmf =
                                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                                        .apply {
                                            init(ks, "".toCharArray())
                                        }

                                // 2. Initialize TLS Context (SSL)
                                sslCtx = SslContextBuilder.forClient()
                                    .keyManager(kmf)
                                    .trustManager(ca) // Netty handles PEM InputStreams directly
                                    .build()


                                // 3. Initialize DTLS Context
                                // Re-open stream for the second context
                                ca.reset()
                                // Build JSSE SSLContext for DTLS
                                dtlsCtx = SSLContext.getInstance("DTLS")
                                dtlsCtx!!.init(
                                    kmf.keyManagers,
                                    trustManagers(ca),
                                    SecureRandom()
                                )
                                ca.close()
                                Log.i(
                                    CM,
                                    "Certificates and SSL/DTLS contexts initialized successfully."
                                )
                            } catch (e: Exception) {
                                Log.e(CM, "Critical failure during Certificate initialization", e)
                            }
                        }
                    }
                }.collect()
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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

}