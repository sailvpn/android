package com.illiad.troad.service.security.client

import android.content.Context
import android.util.Log
import com.illiad.troad.Consts.TM
import com.illiad.troad.model.AutoRenew
import com.illiad.troad.service.Settings
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.security.Cryptos
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
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

    private var renewJob: Job? = null
    private val clientFactory: HttpClientFactory = createPlatformHttpClientFactory()

    /**
     * Accepts a stable snapshot of the active settings.
     * Manages the lifecycle of the one-time alarm loop.
     */
    fun processSettingsUpdate(settings: Settings) {
        // 1. If autoRenew is NEVER (0 mins) or crypto isn't JWT, cancel any pending alarm chains instantly
        if (settings.autoRenew == AutoRenew.NEVER || settings.crypto != Cryptos.JWT) {
            if (renewJob?.isActive == true) {
                Log.i(TM, "Auto-renew disabled or not in JWT mode. Stopping background loops.")
                stopLifecycleTracking()
            }
            return
        }

        // 2. DEADLOCK FIX: We do NOT optimize or early-return based on interval equality anymore!
        // Every settings update cancels the old chain and schedules a fresh one-time check immediately.
        renewJob?.cancel()

        Log.i(TM, "Scheduling initial one-time token refresh validation alarm...")
        renewJob = scope.launch {
            runOneTimeAlarmChain(settings)
        }
    }

    /**
     * Recursive-style coroutine loop that handles single-shot triggers sequentially.
     */
    private suspend fun CoroutineScope.runOneTimeAlarmChain(settings: Settings) {
        if (!isActive) return

        // 1. Calculate the delay dynamically before executing the current pass
        val intervalMinutes = settings.autoRenew?.minutes ?: 5L
        val intervalMs = intervalMinutes * 60 * 1000L

        // 2. Sleep for this single-shot window
        delay(intervalMs)

        Log.d(TM, "One-time refresh alarm triggered. Evaluating live token lifecycle...")

        // 3. Process the refresh transaction safely
        runCatching {
            executeAutoRenew(settings)
        }.onFailure { e ->
            Log.e(TM, "One-time token renewal execution failed", e)
        }

        // 4. SELF-SCHEDULING CHAIN LINK:
        // Instead of a periodic cycle, we read the freshest storage snapshot and schedule
        // the NEXT one-time alarm link only after the current processing completes.
        if (isActive) {
            Log.d(TM, "Scheduling next one-time alarm trigger link...")
            runOneTimeAlarmChain(settings)
        }
    }

    /**
     * Centralized execution logic for updating an active token lifecycle.
     */
    private suspend fun executeAutoRenew(snapshot: Settings) {
        val jwt = snapshot.jwt
        if (!jwt.isNullOrEmpty()) {
            val expiresAt = getExpireInstant(jwt)
            val now = Clock.System.now()
            val remainingMinutes = (expiresAt - now).inWholeMinutes

            // Safety margin lookup window matches your forward loop interval tick
            val intervalMinutes = snapshot.autoRenew?.minutes ?: 5L
            val bufferMinutes = 1L
            val criticalThresholdMinutes = intervalMinutes + bufferMinutes

            // Check if the token is entering its critical expiration window
            if (now < expiresAt && remainingMinutes < criticalThresholdMinutes) {
                Log.i(
                    TM,
                    "Token entering critical expiration window ($remainingMinutes mins left). Proactively refreshing..."
                )
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

        // Trigger full token acquisition fallback if token refresh fails or is absent
        val fallbackSuccess = tryToAcquireToken(snapshot)
        if (!fallbackSuccess) throw Exception("Out-of-band credential authentication rejected by server.")
    }

    private suspend fun tryToAcquireToken(snapshot: Settings): Boolean {
        val user = tStore.usernameFlow.firstOrNull()
        val pass = tStore.passwordFlow.firstOrNull()

        if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {
            Log.d(TM, "Exchanging raw credentials for fresh security token...")
            return postGenerate(
                snapshot,
                TokenGenerateRequest(
                    username = user,
                    password = pass,
                    expirationMinutes = snapshot.duration?.minutes ?: 60L
                )
            )
        }
        return false
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


