package com.illiad.troad.service.security.client

import android.content.Context
import android.util.Log
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.illiad.troad.Consts.TM
import com.illiad.troad.Utils
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.security.CertManager
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.http.HttpProtocol
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

import kotlin.concurrent.Volatile

import kotlinx.coroutines.reactor.mono
import reactor.core.scheduler.Schedulers

// 1. Change 'object' to 'class' with a primary constructor
class TokenManager private constructor(context: Context) {

    // Store applicationContext to prevent leaking an Activity context
    private val appContext = context.applicationContext
    private val tStore by lazy { TroadStore(appContext) }
    private val objMapper: ObjectMapper = createObjectMapper()

    // 1. Define the client using the lazy delegate
    private val client: HttpClient by lazy {
        // 2. Configure the connection pool
        val connectionProvider = ConnectionProvider.builder("proxy-client-pool")
            .maxConnections(100)
            .pendingAcquireMaxCount(500)
            .build()

        // 3. Create and return the configured client
        HttpClient.create(connectionProvider)
            .compress(true)
            .protocol(HttpProtocol.HTTP11)
            .secure { spec ->
                spec.sslContext(CertManager.sslCtx!!)
            }
    }

    @Volatile
    private var running = false

    @Volatile
    private var renewDisposable: Disposable? = null

    /**
     * Centralized POST to token/generate and parse into TokenHolder.
     * Ensures file writes happen on boundedElastic scheduler.
     */
    // Java logic, but in Kotlin JVM (no Native complexity)
    private fun postGenerate(requestBytes: ByteArray): Mono<Data> {
        val uri =
            "https://" + Utils.settings?.domain + ":" + Utils.settings?.port + "/api/auth/token/generate"

        return client
            // Fix for the 'headers' clause in Kotlin
            .headers { h -> h.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON) }
            .post()
            .uri(uri)
            .send(Mono.just(Unpooled.wrappedBuffer(requestBytes)))
            .responseSingle { response, byteBufMono ->
                byteBufMono.asString(StandardCharsets.UTF_8).flatMap { body ->
                    val code = response.status().code()
                    if (code in 200..299) {
                        try {
                            val res = objMapper.readValue(body, TokenResponse::class.java)
                            Mono.just(res)
                        } catch (e: Exception) {
                            Mono.error(e)
                        }
                    } else {
                        Mono.error(RuntimeException("HTTP $code: $body"))
                    }
                }
            }
            .flatMap { response ->
                // Use 'mono { }' to bridge to your Coroutine-based TroadStore
                mono {
                    val data = response.data
                    if (data?.token != null) {
                        // This calls your TroadStore suspend function
                        tStore.saveJwt(data.token)
                    }
                    data // Return the Data object back to the Mono stream
                }
            }
            .subscribeOn(Schedulers.boundedElastic())
    }

    /**
     * Initialize token management - acquire initial token and start renewal
     */
    @Throws(JsonProcessingException::class)
    fun initialize() {
        // Structural equality check with '==' (handles null safely)
        if (Utils.settings?.crypto?.value != "JWT") {
            Log.i(TM, "Token management disabled (crypto != JWT)")
            return
        }
        if (Utils.settings!!.autoRenew!!.minutes > 0L) {
            Log.i(TM, "Automatic token mode enabled (tokenMode=auto)")
            val expiresAt = getExpireEpochMilli(Utils.settings!!.jwt)

            // If no token present or expired
            if (Instant.now().isAfter(expiresAt)) {
                val username = Utils.settings!!.username
                val password = Utils.settings!!.password

                if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
                    throw IllegalStateException("Automatic token mode requires username/password when no valid token is available")
                }
                val requestBytes = objMapper.writeValueAsBytes(
                    TokenGenerateRequest(
                        username = username,
                        password = password,
                        expirationMinutes = Utils.settings!!.duration!!.minutes
                    )
                )

                try {
                    // block() is still available in Kotlin for Reactor types
                    postGenerate(requestBytes)
                        .timeout(Duration.ofSeconds(10))
                        .block()

                } catch (e: Exception) {
                    throw IllegalStateException("Failed to acquire initial token", e)
                }
            } else if (Duration.between(Instant.now(), expiresAt).toMinutes() < 1080L) {
                // Proactively renew asynchronously
                try {
                    val requestBytes = objMapper.writeValueAsBytes(
                        TokenGenerateRequest(
                            currentToken = Utils.settings!!.jwt,
                            expirationMinutes = Utils.settings!!.duration!!.minutes
                        )
                    )
                    postGenerate(requestBytes)
                        .timeout(Duration.ofSeconds(10))
                        .block()

                } catch (e: JsonProcessingException) {
                    Log.e(TM, "Failed to serialize renewal request", e)
                    throw e
                } catch (e: Exception) {
                    Log.e(TM, "Failed to renew token", e)
                    throw e
                }
            } else {
                Log.i(TM, "Failed to build token renewal request")
            }
        }
        startAutoRenewal()
    }

    /**
     * Start periodic token renewal using Reactor's Flux.interval and track the Disposable.
     */
    private fun startAutoRenewal() {
        if (running) return
        running = true

        val renewInterval = 60L
        Log.i(TM, "Starting token renewal every {renewInterval} minutes")

        renewDisposable = Flux.interval(
            Duration.ofMinutes(renewInterval),
            Duration.ofMinutes(renewInterval),
            Schedulers.parallel()
        )
            .flatMap { tick ->
                try {
                    val requestBytes = objMapper.writeValueAsBytes(
                        TokenGenerateRequest(
                            currentToken = Utils.settings!!.jwt,
                            expirationMinutes = Utils.settings!!.duration!!.minutes
                        )
                    )

                    postGenerate(requestBytes)
                        .timeout(Duration.ofSeconds(10))
                } catch (e: JsonProcessingException) {
                    Log.e(TM, "Failed to serialize renewal request", e)
                    Mono.empty()
                }
            }
            .subscribe()
    }

    /**
     * Stop token renewal
     */
    fun shutdown() {
        running = false
        if (renewDisposable != null && !renewDisposable!!.isDisposed()) {
            renewDisposable!!.dispose()
        }
    }

    private fun getExpireEpochMilli(jwt: String?): Instant {
        // .get() on an AtomicReference or custom wrapper
        val claims = parseJWT(jwt)

        // claims?.get(...) handles null check; the second param specifies the class type
        val expiresAt = claims?.get("expiresAt", java.lang.Long::class.java)

        return if (expiresAt != null) {
            Instant.ofEpochMilli(expiresAt.toLong())
        } else {
            Instant.EPOCH // treat as expired if we can't parse
        }
    }

    private fun parseJWT(jwtString: String?): Claims? {
        if (jwtString.isNullOrBlank()) return null

        // Standard JJWT trick: remove signature to treat as unsecured
        val lastDotIndex = jwtString.lastIndexOf('.')
        if (lastDotIndex > 0) {
            val withoutSignature = jwtString.substring(0, lastDotIndex + 1)

            return try {
                Jwts.parser()
                    .unsecured() // Required in JJWT 0.12+ to allow unsigned tokens
                    .build()
                    .parseUnsecuredClaims(withoutSignature)
                    .payload
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    companion object {
        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(context: Context): TokenManager {
            return instance ?: synchronized(this) {
                instance ?: TokenManager(context).also { instance = it }
            }
        }

        private fun createObjectMapper(): ObjectMapper {
            val mapper: ObjectMapper = ObjectMapper()
            mapper.registerModule(JavaTimeModule())
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            return mapper
        }
    }
}