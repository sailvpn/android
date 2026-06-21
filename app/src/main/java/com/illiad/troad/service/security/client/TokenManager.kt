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
     * Synchronously ensures that a valid token exists in storage.
     * If the current token is missing or expired, it forces an out-of-band network refresh.
     * Returns true if a valid token is ready, false if authentication failed.
     */
    suspend fun ensureValidToken(snapshot: Settings): Boolean {
        // 1. CRITICAL VALIDATION CHECK:
        // If the snapshot itself is invalid (missing domain, bad port, etc.),
        // abort early to prevent the VPN engine from crashing.
        if (!snapshot.isValid) {
            Log.w(TM, "Pre-flight check aborted: Settings snapshot failed validation rules.")
            return false
        }

        // 2. PROTOCOL TYPE ROUTING:
        // If the crypto mode doesn't use JWT, it's immediately safe to proceed to socket creation
        if (snapshot.crypto != Cryptos.JWT || snapshot.autoRenew == AutoRenew.NEVER) return true

        // 3. CACHED TOKEN VALIDATION:
        val jwt = snapshot.jwt
        if (!jwt.isNullOrEmpty()) {
            val expiresAt = getExpireInstant(jwt)
            val now = Clock.System.now()

            // If the token is still alive and has a safe cushion (> 5 minute left), proceed instantly
            if (now < expiresAt && (expiresAt - now).inWholeMinutes > 5L) {
                Log.d(TM, "Pre-flight check passed: Token is still valid.")
                return true
            }
        }

        // If we reach here, the token is either null, empty, or expired.
        Log.i(
            TM,
            "Pre-flight check failed: Token missing or expired. Forcing out-of-band renewal..."
        )

        // 4. SECURE DATASTORE FALLBACK:
        // look up the raw user credentials out-of-band from the DataStore
        val user = tStore.usernameFlow.firstOrNull()
        val pass = tStore.passwordFlow.firstOrNull()

        if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {
            Log.d(TM, "Forced renewal falling back to saved user credentials...")
            return postGenerate(
                snapshot,
                TokenGenerateRequest(
                    username = user,
                    password = pass,
                    expirationMinutes = snapshot.duration?.minutes ?: 60L
                )
            )
        }

        Log.w(TM, "Forced pre-flight renewal failed: No valid credentials or tokens found.")
        return false
    }

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
    private fun getExpireInstant(jwt: String): Instant {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return Instant.DISTANT_PAST
            val payload = Base64.decode(parts[1]).decodeToString()
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


