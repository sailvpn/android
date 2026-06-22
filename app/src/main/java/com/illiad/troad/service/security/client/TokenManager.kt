package com.illiad.troad.service.security.client

import android.content.Context
import android.util.Log
import com.illiad.troad.Consts.TM
import com.illiad.troad.model.AutoRenew
import com.illiad.troad.service.Settings
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.SettingsUseCase
import com.illiad.troad.service.security.Cryptos
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.firstOrNull
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

    // INSTANT SIGNALING CHANNEL:
    // Acts as a thread-safe alarm clock. Sending a signal here unblocks the sleep timer instantly.
    // Channel.CONFLATED ensures rapid sequential setting updates collapse into a single wake-up trigger.
    private val wakeUpChannel = Channel<Unit>(Channel.CONFLATED)

    /**
     * Entry hook from the Butler stream.
     * Fires on EVERY configuration change. Instantly signals the running loop to adapt.
     */
    fun processSettingsUpdate(settingsUseCase: SettingsUseCase) {
        // 1. If no loop is active, spin up the baseline worker engine thread
        if (renewJob?.isActive != true) {
            Log.i(TM, "Spawning autonomous, instant-response background renewal alarm chain...")
            renewJob = scope.launch {
                runFlatAlarmLoop(settingsUseCase)
            }
            return
        }

        // 2. INSTANT INTERCEPT: If a loop IS already active, send a signal down the channel.
        // This wakes up the sleeping delay block instantly so it can check the fresh config!
        Log.d(TM, "Settings change captured. Issuing instant wake-up signal to channel.")
        wakeUpChannel.trySend(Unit)
    }

    /**
     * FLAT AUTONOMOUS ALARM ENGINE:
     * Runs indefinitely, using a channel-backed timer to react to updates in under a millisecond.
     */
    private suspend fun runFlatAlarmLoop(settingsUseCase: SettingsUseCase) {
        while (true) {
            // A. SINGLE-SHOT DATA CAPTURE: Check baseline variables straight from storage
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
                Log.i(TM, "Auto-renew disabled or non-JWT protocol detected. Exiting alarm loop cleanly.")
                break
            }

            val intervalMs = intervalMinutes * 60 * 1000L
            Log.d(TM, "Scheduling smart-sleep window. Target: $intervalMinutes minutes ($intervalMs ms).")

            // C. CRASH-PROOF INSTANT SMART-SLEEP WINDOW:
            // STALE BUFFER DRAINAGE: Flush old tokens stuck in the buffer while the engine was busy
            // executing previous network tasks or reading database settings snapshots.
            while (wakeUpChannel.tryReceive().isSuccess) {
                Log.d(TM, "Draining stale wake-up signal from channel buffer...")
            }

            var executionSettings: Settings?

            // PURE NATIVE COROUTINE SUSPENSION:
            // No try-catch needed!
            // - If the timer runs out, it unblocks naturally and returns null.
            // - If a setting changes, wakeUpChannel.receive() unblocks naturally and returns Unit.
            // - If the VPN disconnects, renewJob?.cancel() throws a CancellationException,
            //   which bypasses the loop automatically to terminate the thread cleanly without leaks.
            withTimeoutOrNull(intervalMs) {
                wakeUpChannel.receive()
                Log.i(TM, "Woken up instantly by channel trigger. Advancing loop to recalibrate timeline.")
            }

            // D. RE-EVALUATION PASSTHROUGH: Fetch newest data reflecting updates on a normal timeout wakeup
            executionSettings = try {
                settingsUseCase().firstOrNull() ?: currentSettings
            } catch (_: Exception) {
                currentSettings
            }

            // Verify auto-renew hasn't been switched off mid-flight before writing over the network
            if (executionSettings.autoRenew == AutoRenew.NEVER || executionSettings.crypto != Cryptos.JWT) {
                Log.i(TM, "Auto-renew disabled or protocol altered post-wake. Exiting loop cleanly.")
                break
            }

            Log.d(TM, "Executing out-of-band renewal transaction pass...")

            // E. EXECUTE CODE: Isolate exceptions safely to protect engine loop continuity
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

            val remainMins = (getExpireInstant(jwt) - Clock.System.now()).inWholeMinutes

            if (remainMins < 0){
                // token somehow already excpired, procure a new one by username/password
                Log.d(TM, "token somehow already expired")
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
                    Log.d(TM, "procured token by user credential...")

                } else {
                    Log.d(TM, "procuring token by user credential failed...")
                }
            } else if (remainMins < (snapshot.autoRenew?.minutes
                    ?: 5L) * 5
            ) {
                // the token entered its critical expiration window
                Log.i(
                    TM,
                    "Token about to expire. Proactively refreshing..."
                )

                if (postGenerate(
                        snapshot,
                        TokenGenerateRequest(
                            currentToken = jwt,
                            expirationMinutes = snapshot.duration?.minutes ?: 60L
                        )
                    )
                ) {
                    Log.d(TM, "token refreshed...")
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
                        Log.d(TM, "token refreshed by user credential...")

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


