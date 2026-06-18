package com.illiad.troad.service

import android.content.Context
import android.util.Log
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.security.Cryptos
import com.illiad.troad.service.security.HeaderEncoder
import com.illiad.troad.service.security.client.TokenGenerateRequest
import com.illiad.troad.service.security.client.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import troadengine.Troadengine

class Butler private constructor(context: Context) {

    interface TunnelInterfaceController {
        fun requestHardRestart(bootHeader: ByteArray)
        fun onPreFlightAuthenticationFailed(reason: String)
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val tStore by lazy { TroadStore(appContext) }
    private val tokenManager by lazy { TokenManager.getInstance(appContext) }
    private val settingsUseCase: SettingsUseCase = SettingsUseCase(SettingsRepoImp(tStore))

    private var observeJob: Job? = null
    private var controllerRef: TunnelInterfaceController? = null

    // WARM CACHE REGISTRY
    // Survives Service destruction; allows instant warm reconnections
    var activeSettings: Settings? = null
        private set

    var header: ByteArray? = null
        private set

    /**
     * Binds the interface listener and kicks off the
     * reactive DataStore stream monitoring cycles.
     */
    fun startMonitoring(controller: TunnelInterfaceController) {
        this.controllerRef = controller

        observeJob?.cancel()
        observeJob = scope.launch {
            settingsUseCase()
                .catch { e -> Log.e("Butler", "Failed resolving configuration stream", e) }
                .collectLatest { cleanSnapshot ->
                    processIncomingSettingsChange(cleanSnapshot)
                }
        }
    }

    /**
     * Processes changes from the data stream and handles hot-provisioning vs restarting logic.
     */
    private suspend fun processIncomingSettingsChange(newSettings: Settings) {
        val previousSettings = activeSettings

        // 1. Sync out-of-band TokenManager refresh intervals continuously
        tokenManager.processSettingsUpdate(newSettings)

        // 2. Identify if structural properties shifted
        val needsHardRestart = previousSettings == null ||
                previousSettings.domain != newSettings.domain ||
                previousSettings.port != newSettings.port ||
                previousSettings.cacert != newSettings.cacert

        // 3. Detect if raw authentication fields changed
        val cryptoChanged = newSettings.crypto != previousSettings?.crypto
        val jwtChanged = newSettings.jwt != previousSettings?.jwt
        val secretChanged = newSettings.secret != previousSettings?.secret

        if (cryptoChanged || jwtChanged || secretChanged || (needsHardRestart && header == null)) {
            Log.i("Butler", "Credential change or cold boot detected. Re-encoding header arrays.")

            val calculatedHeader = HeaderEncoder.encode(
                newSettings.crypto,
                newSettings.secret,
                newSettings.jwt
            )

            // Commit fresh snapshots to the local warm cache fields
            activeSettings = newSettings
            if (calculatedHeader != null) {
                header = calculatedHeader
            }

            // 4. Hot-provision instantly if the Go engine core is actively executing
            if (!needsHardRestart && Troadengine.isRunning()) {
                try {
                    Troadengine.updateHeader(header)
                    Log.d("Butler", "Go core running. Hot-provisioned fresh bytes successfully.")
                } catch (e: Exception) {
                    Log.e("Butler", "JNI hot-provision loop failure", e)
                }
            }
        } else {
            // Keep metadata details (like connection duration limits) updated without overwriting compiled header
            activeSettings = newSettings
        }

        // 5. If structural elements changed while running, request the service to cycle interfaces
        if (needsHardRestart && previousSettings != null && Troadengine.isRunning()) {
            withContext(Dispatchers.Main) {
                controllerRef?.requestHardRestart(header ?: byteArrayOf())
            }
        }
    }

    /**
     * Executes a one-shot, pre-flight authorization verification right before booting the VPN.
     * If the current token is missing or expired, it uses username/password out-of-band to bootstrap a fresh one.
     * Ensures the class-level 'header' field is populated with a valid key before exiting.
     * Returns true on success, false if the server rejects authentication.
     */
    suspend fun prepareTunnelCredentials(): Boolean {
        // 1. Fetch initial configuration context from disk
        var currentSettings = activeSettings ?: try {
            val fetched = settingsUseCase().first()
            activeSettings = fetched
            fetched
        } catch (e: Exception) {
            Log.e("Butler", "Failed to fetch cold-start baseline settings", e)
            return false
        }

        // 2. Validate structural integrity of the parameters first
        if (!currentSettings.isValid) {
            Log.w("Butler", "Pre-flight aborted: Configuration snapshot is missing parameters.")
            return false
        }

        // 3. Protocol Router: If it doesn't use JWT, immediately generate the static key header and exit
        if (currentSettings.crypto != Cryptos.JWT) {
            Log.i("Butler", "Pre-flight passed for static crypto protocol. Compiling handshake key.")
            header = HeaderEncoder.encode(currentSettings.crypto, currentSettings.secret, currentSettings.jwt)
            return header != null
        }

        // 4. JWT Token Longevity Analysis
        val jwt = currentSettings.jwt
        var tokenIsValid = false

        if (!jwt.isNullOrEmpty()) {
            val expiresAt = tokenManager.getExpireInstant(jwt)
            val now = Clock.System.now()

            // Ensure token has a safe 1-minute transmission margin left
            if (now < expiresAt && (expiresAt - now).inWholeMinutes > 1L) {
                Log.d("Butler", "Pre-flight check passed: Token cache is still hot and valid.")
                tokenIsValid = true
            }
        }

        // 5. One-Shot Out-of-Band Forced Bootstrapping
        if (!tokenIsValid) {
            Log.i("Butler", "Token missing or dead at boot. Initiating one-shot out-of-band credential exchange...")

            // Attempt immediate token-swap refresh using old JWT if available
            var refreshSuccess = false
            if (!jwt.isNullOrEmpty()) {
                refreshSuccess = tokenManager.postGenerate(
                    currentSettings,
                    TokenGenerateRequest(
                        currentToken = jwt,
                        expirationMinutes = currentSettings.duration?.minutes ?: 60L
                    )
                )
            }

            // Complete Fallback: Fetch credentials single-shot from secure storage to resolve token
            if (!refreshSuccess) {
                val user = tStore.usernameFlow.firstOrNull()
                val pass = tStore.passwordFlow.firstOrNull()

                if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {
                    Log.d("Butler", "Exchanging raw user credentials for initial bootstrap token...")
                    refreshSuccess = tokenManager.postGenerate(
                        currentSettings,
                        TokenGenerateRequest(
                            username = user,
                            password = pass,
                            expirationMinutes = currentSettings.duration?.minutes ?: 60L
                        )
                    )
                }
            }

            if (!refreshSuccess) {
                Log.w("Butler", "Forced one-shot bootstrap failed. Server rejected parameters.")
                withContext(Dispatchers.Main) {
                    controllerRef?.onPreFlightAuthenticationFailed("Authentication rejected by remote proxy.")
                }
                return false
            }

            // 6. Force a quick re-read from DataStore to fetch the freshly minted token string
            try {
                currentSettings = settingsUseCase().first()
                activeSettings = currentSettings
            } catch (e: Exception) {
                Log.e("Butler", "Failed collecting post-auth token update from disk", e)
                return false
            }
        }

        // 7. Success State Assembly: Populate the class field directly and quit
        header = HeaderEncoder.encode(
            currentSettings.crypto,
            currentSettings.secret,
            currentSettings.jwt
        )

        return header != null
    }


    /**
     * Gracefully cuts down tracking loops when the VPN disconnects.
     * Retains 'activeSettings' and 'header' as a warm cache for instant reconnection.
     */
    fun stopMonitoring() {
        tokenManager.stopLifecycleTracking()
        observeJob?.cancel()
        controllerRef = null
    }

    /**
     * Optional explicit purge routine (useful ONLY for explicit user logouts or account switching)
     */
    fun clearCache() {
        Log.d("Butler", "Explicitly clearing warm memory state registry...")
        activeSettings = null
        header = null
    }

    companion object {
        @Volatile
        private var instance: Butler? = null

        fun getInstance(context: Context): Butler =
            instance ?: synchronized(this) {
                instance ?: Butler(context).also { instance = it }
            }
    }
}
