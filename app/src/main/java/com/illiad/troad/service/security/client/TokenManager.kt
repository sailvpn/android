package com.illiad.troad.service.security.client

import android.content.Context
import android.util.Log
import com.illiad.troad.Consts.TM
import com.illiad.troad.model.AutoRenew
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.Settings
import com.illiad.troad.service.security.Cryptos
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
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

    private var currentIntervalMs: Long = 0L
    private var renewJob: Job? = null

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
        }
    }

    /**
     * Accepts a stable snapshot of the active settings.
     * Keeps background clocks aligned with user choice.
     */
    fun processSettingsUpdate(settings: Settings) {

        // Rule: If autoRenew is set to NEVER (0 mins) or crypto isn't JWT, stop background timers immediately
        if (settings.autoRenew == AutoRenew.NEVER || settings.crypto != Cryptos.JWT) {
            if (renewJob?.isActive == true) {
                Log.i(TM, "Auto-renew disabled or not in JWT mode. Stopping background loops.")
                stopLifecycleTracking()
            }
            return
        }

        // Convert the enum minutes to milliseconds for the coroutine channel
        val nextIntervalMs = settings.autoRenew!!.minutes * 60 * 1000L

        // Optimization: Do nothing if the schedule matches what's already running
        if (nextIntervalMs == currentIntervalMs) return

        // user had input a new renew interval
        currentIntervalMs = nextIntervalMs
        renewJob?.cancel()

        Log.i(TM, "Starting auto token renewal loop. Interval: ${settings.autoRenew.label}")
        renewJob = scope.launch {
            while (isActive) {
                delay(currentIntervalMs)
                runCatching {
                    executeAutoRenew(settings)
                }.onFailure { e ->
                    Log.e(TM, "Periodic out-of-band renewal cycle failed", e)
                }
            }
        }
    }

    private suspend fun postGenerate(settings: Settings, request: TokenGenerateRequest): Boolean {
        val url = "https://${settings.domain}:${settings.port}/api/auth/token/generate"

        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                val resBody = response.body<TokenResponse>()
                val freshToken = resBody.data?.token
                if (!freshToken.isNullOrEmpty()) {
                    tStore.saveJwt(freshToken) // Pure single point of persistence
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TM, "Token HTTP payload transaction failed", e)
            false
        }
    }

    private suspend fun executeAutoRenew(snapshot: Settings) {
        val jwt = snapshot.jwt

        if (!jwt.isNullOrEmpty()) {
            val expiresAt = getExpireInstant(jwt)
            val now = Clock.System.now()

            // Calculate exact time remaining before the token dies
            val remainingMinutes = (expiresAt - now).inWholeMinutes

            // Look forward to the next loop tick, adding a 1-minute network transit safety buffer
            val bufferMinutes = 1L
            val criticalThresholdMinutes = snapshot.autoRenew!!.minutes + bufferMinutes

            if (now < expiresAt && remainingMinutes < criticalThresholdMinutes) {
                Log.i(TM, "Token expiring soon ($remainingMinutes mins left). Proactively refreshing via existing JWT...")
                val success = postGenerate(
                    snapshot,
                    TokenGenerateRequest(
                        currentToken = jwt,
                        expirationMinutes = snapshot.duration?.minutes ?: 60L
                    )
                )
                if (success) return
            }
        }

        // Out-Of-Band Fallback: Fetch credentials safely straight from DataStore using single-shot collection
        Log.i(TM, "JWT refresh skipped or failed. Fetching credentials out-of-band from data store...")
        val user = tStore.usernameFlow.firstOrNull()
        val pass = tStore.passwordFlow.firstOrNull()

        if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {
            Log.d(TM, "Attempting token generation via saved user credentials...")
            val success = postGenerate(
                snapshot,
                TokenGenerateRequest(
                    username = user,
                    password = pass,
                    expirationMinutes = snapshot.duration?.minutes ?: 60L
                )
            )
            if (!success) throw Exception("Credential authentication rejected by remote server.")
        } else {
            Log.w(TM, "Auto-renew execution abandoned: Data store credentials are blank.")
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun getExpireInstant(jwt: String): Instant {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return Instant.DISTANT_PAST

            val payload = Base64.decode(parts[1]).decodeToString()
            val json = Json.parseToJsonElement(payload).jsonObject
            val exp = json["exp"]?.jsonPrimitive?.longOrNull ?: return Instant.DISTANT_PAST

            Instant.fromEpochSeconds(exp)
        } catch (e: Exception) {
            Instant.DISTANT_PAST
        }
    }

    fun stopLifecycleTracking() {
        renewJob?.cancel()
        currentIntervalMs = 0L
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
