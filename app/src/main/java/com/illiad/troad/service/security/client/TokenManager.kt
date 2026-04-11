package com.illiad.troad.service.security.client

import android.content.Context
import android.util.Log
import com.illiad.troad.Consts.TM
import com.illiad.troad.Utils
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.security.Cryptos
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class TokenManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val tStore by lazy { TroadStore(appContext) }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var interval: Long = 0L
    private var renewJob: Job? = null

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
        }
    }

    suspend fun manageRenew(renew: Long) {
        if (renew == interval) return
        interval = renew
        renewJob?.cancel()

        if (interval > 0L && Cryptos.JWT == Utils.settings?.crypto) {
            Log.i(TM, "Update automatic token renew")
            renewJob = scope.launch {
                while (isActive) {
                    delay(interval)
                    runCatching { doAutoRenew() }
                }
            }
        }
    }

    private suspend fun postGenerate(request: TokenGenerateRequest): Data? {
        val url = "https://${Utils.settings?.domain}:${Utils.settings?.port}/api/auth/token/generate"

        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            val resBody = response.body<TokenResponse>()
            resBody.data?.token?.let { tStore.saveJwt(it) }
            resBody.data
        } catch (e: Exception) {
            Log.e(TM, "Token post failed", e)
            null
        }
    }


    private suspend fun doAutoRenew() {
        val jwt = Utils.settings?.jwt
        if (!jwt.isNullOrEmpty()) {
            val expiresAt = getExpireInstant(jwt)
            val now = Clock.System.now()

            // Logic: if current time is before expiry but within the renewal window
            if (now < expiresAt && (expiresAt - now).inWholeMinutes < (10 * interval / 60000)) {
                val data = postGenerate(
                    TokenGenerateRequest(
                        currentToken = jwt,
                        expirationMinutes = Utils.settings!!.duration?.minutes ?: 60
                    )
                ) ?: throw Exception("Failed renewing token!")
                tStore.saveJwt(data.token!!)
                return
            }
        }

        // Fallback to credentials
        val user = Utils.settings?.username
        val pass = Utils.settings?.password
        if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {
            val data = postGenerate(
                TokenGenerateRequest(
                    username = user,
                    password = pass,
                    expirationMinutes = Utils.settings!!.duration?.minutes ?: 60
                )
            ) ?: throw Exception("Failed renewing token!")
            tStore.saveJwt(data.token!!)
        }

    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun getExpireInstant(jwt: String): Instant {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return Instant.DISTANT_PAST

            // 1. Decode the payload (middle part of the JWT)
            val payload = Base64.decode(parts[1]).decodeToString()
            val json = Json.parseToJsonElement(payload).jsonObject

            // 2. Extract standard "exp" (Seconds)
            val exp = json["exp"]?.jsonPrimitive?.longOrNull ?: return Instant.DISTANT_PAST

            // 3. Convert Seconds to Instant
            Instant.fromEpochSeconds(exp)
        } catch (e: Exception) {
            Instant.DISTANT_PAST
        }
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
