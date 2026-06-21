package com.illiad.troad.service.security.client

import android.content.Context
import android.util.Log
import com.illiad.troad.Consts.TM
import com.illiad.troad.service.Settings
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.SettingsUseCase
import com.illiad.troad.service.security.Cryptos
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class TokenManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val tStore by lazy { TroadStore(appContext) }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var renewJob: Job? = null
    private val clientFactory: HttpClientFactory = createPlatformHttpClientFactory()

    /**
     * Initializes the autonomous background token refresh loop.
     * Runs continuously, monitoring data stream parameters natively without external resets.
     */
    fun processSettingsUpdate(settingsUseCase: SettingsUseCase) {
        // 1. If an active loop is already running, do absolutely nothing.
        // The running while(true) loop will catch updates internally on its own timeline!
        if (renewJob?.isActive == true) return

        Log.i(TM, "Spawning autonomous, flat self-scheduling background renewal alarm chain...")
        renewJob = scope.launch {
            runFlatAlarmLoop(settingsUseCase)
        }
    }

    /**
     * RESPONSIVE AUTONOMOUS ALARM ENGINE:
     * Runs indefinitely, using scoped cancellations inside the loop to adapt to timeline parameters instantly.
     */
    private suspend fun runFlatAlarmLoop(settingsUseCase: SettingsUseCase) {
        while (true) {
            // A. SINGLE-SHOT DATA CAPTURE: Check baseline configuration variables straight from storage
            val currentSettings = try {
                settingsUseCase().firstOrNull()
            } catch (e: Exception) {
                Log.e(TM, "Alarm loop failed reading configuration from storage", e)
                delay(10000)
                continue
            }

            // B. PROTOCOL INTERCEPTOR GATEWAYS: Exit loop cleanly if disabled
            val intervalMinutes = currentSettings?.autoRenew?.minutes ?: 0L
            if (intervalMinutes == 0L || currentSettings?.crypto != Cryptos.JWT) {
                Log.i(
                    TM,
                    "Auto-renew disabled or non-JWT protocol detected. Exiting alarm loop cleanly."
                )
                break
            }

            val intervalMs = intervalMinutes * 60 * 1000L
            Log.d(
                TM,
                "Scheduling smart-sleep window. Target: $intervalMinutes minutes ($intervalMs ms)."
            )

            var executionSettings = currentSettings

            // C. INTERACTING INTERCEPTOR WRAPPER:
            // We place the try-catch INSIDE the while(true) loop, wrapped precisely around the timer block.
            try {
                withTimeoutOrNull(intervalMs) {
                    settingsUseCase().collectLatest { liveUpdate ->
                        if (liveUpdate.autoRenew?.minutes != intervalMinutes || liveUpdate.crypto != currentSettings.crypto) {
                            Log.i(
                                TM,
                                "Critical scheduling parameter shift detected during sleep cycle. Recalibrating timeline instantly..."
                            )

                            // Break out of the local withTimeoutOrNull block immediately.
                            // This unblocks the sleep timer without killing your main outer background thread!
                            throw CancellationException("Timeline shifted")
                        } else {
                            // Maintain freshest tokens or duration properties while sleeping
                            executionSettings = liveUpdate
                        }
                    }
                }
            } catch (e: CancellationException) {
                // D. RE-ALIGNMENT BRIDGE:
                // Check if the parent coroutine scope itself is being cancelled (e.g., user clicked disconnect).
                // If the whole service is shutting down, we must respect that and re-throw the exception to exit.
                if (!currentCoroutineContext().isActive) {
                    throw e
                }

                // Otherwise, it was just a local setting adjustment. Log it, let the catch block clear,
                // and the while(true) loop will natively advance straight back to step A to apply the updates!
                Log.d(
                    TM,
                    "Local sleep container unblocked successfully. Re-cycling loop for fresh parameter allocation."
                )
                continue
            }

            Log.d(TM, "Alarm link triggered normally. Processing out-of-band renewal...")

            // E. EXECUTE THE TRANSACTION: Bubble network exceptions safely to protect loop framework continuity
            try {
                executeAutoRenew(executionSettings)
            } catch (e: Exception) {
                Log.e(TM, "Background token renewal pass failed. Safe-continuing loop track.", e)
            }
        }
    }


    /**
     * Centralized execution logic for updating an active token lifecycle.
     */
    private suspend fun executeAutoRenew(snapshot: Settings) {

        try {
            val jwt = snapshot.jwt

            // Check if the token is entering its critical expiration window
            if ((getExpireInstant(jwt) - Clock.System.now()).inWholeMinutes < (snapshot.autoRenew?.minutes
                    ?: 5L) * 5
            ) {
                Log.i(
                    TM,
                    "Token entering critical expiration window. Proactively refreshing..."
                )

                if (postGenerate(
                        snapshot,
                        TokenGenerateRequest(
                            currentToken = jwt,
                            expirationMinutes = snapshot.duration?.minutes ?: 60L
                        )
                    )
                ) {
                    Log.d(TM, "refresh token...")
                } else {
                    val user = tStore.usernameFlow.firstOrNull()
                    val pass = tStore.passwordFlow.firstOrNull()
                    if (user.isNullOrEmpty() && !pass.isNullOrEmpty() && postGenerate(
                            snapshot,
                            TokenGenerateRequest(
                                username = user,
                                password = pass,
                                expirationMinutes = snapshot.duration?.minutes ?: 60L
                            )
                        )
                    ) {
                        Log.d(TM, "refresh token by user credential...")

                    } else {
                        Log.d(TM, "refreshing token failed...")
                    }
                }
            }
        } catch (e: Exception) {
            throw Exception(TM, e)
        }
    }

    suspend fun postGenerate(settings: Settings, request: TokenGenerateRequest): Boolean {
        val url = "https://${settings.domain}:${settings.port}/api/auth/token/generate"
        val freshCacertPemStr = tStore.caCertFlow.firstOrNull()
        val executionClient = clientFactory.createSecureClient(freshCacertPemStr)

        return try {
            val response = executionClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                val resBody = response.body<TokenResponse>()
                val freshToken = resBody.data?.token
                if (!freshToken.isNullOrEmpty()) {
                    tStore.saveJwt(freshToken)
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TM, "Token HTTP payload transaction failed", e)
            false
        } finally {
            executionClient.close()
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun getExpireInstant(jwt: String?): Instant {
        if (jwt.isNullOrEmpty()) {
            return Instant.DISTANT_PAST
        }
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return Instant.DISTANT_PAST

            var payloadPart = parts[1]

            // 1. DYNAMIC PADDING FIX: Re-add missing '=' padding characters
            // if the URL-safe token stripped them out.
            val missingPadding = payloadPart.length % 4
            if (missingPadding > 0) {
                payloadPart += "=".repeat(4 - missingPadding)
            }

            // 2. USE URL-SAFE DECODER: Standard JWT payloads use URL-Safe base64 specifications
            val payloadBytes = Base64.UrlSafe.decode(payloadPart)
            val payload = payloadBytes.decodeToString()

            val json = Json.parseToJsonElement(payload).jsonObject
            val exp = json["exp"]?.jsonPrimitive?.longOrNull ?: return Instant.DISTANT_PAST

            Instant.fromEpochSeconds(exp)
        } catch (_: Exception) {
            Instant.DISTANT_PAST
        }
    }


    fun stopLifecycleTracking() {
        renewJob?.cancel()
    }

    companion object {
        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(context: Context): TokenManager =
            instance ?: synchronized(this) {
                instance ?: TokenManager(context).also { instance = it }
            }
    }
}


