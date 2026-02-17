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
import com.illiad.troad.service.security.Cryptos
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import kotlinx.coroutines.reactor.mono
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.netty.http.HttpProtocol
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.Volatile
import kotlin.concurrent.scheduleAtFixedRate

class TokenManager private constructor(context: Context) {

    // Store applicationContext to prevent leaking an Activity context
    private val appContext = context.applicationContext
    private val tStore by lazy { TroadStore(appContext) }
    private val objMapper: ObjectMapper = createObjectMapper()
    private var interval: Long = 0L

    @Volatile
    private var running = false

    @Volatile
    private var renewer: TimerTask? = null

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

    fun manageRenew(renew: Long) {

        // do nothing if renew interval hasn't changed
        if (renew != interval) {
            interval = renew
            renewer?.cancel()
            if (interval > 0L && Cryptos.JWT == Utils.settings?.crypto) {
                Log.i(TM, " update automatic token renew")
                renewer = Timer().scheduleAtFixedRate(interval, interval) {
                    doAutoRenew()
                }
                running = true
            } else {
                running = false
            }
        }
    }

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
    fun doAutoRenew() {
        try {

            if (Utils.settings!!.jwt != null) {
                val jwt = Utils.settings!!.jwt
                if (!jwt!!.isEmpty()) {
                    val expiresAt = getExpireEpochMilli(jwt)

                    val now = Instant.now()
                    // If no token present or expired
                    if (now.isBefore(expiresAt) && Duration.between(now, expiresAt)
                            .toMinutes() < 10 * interval
                    ) {
                        val requestBytes = objMapper.writeValueAsBytes(
                            TokenGenerateRequest(
                                currentToken = jwt,
                                expirationMinutes = Utils.settings!!.duration?.minutes
                                    ?: com.illiad.troad.model.Duration.DEFAULT.minutes
                            )
                        )
                        // block() is still available in Kotlin for Reactor types
                        postGenerate(requestBytes)
                            .timeout(Duration.ofSeconds(10))
                            .block()
                        return
                    }
                }
            }
        } catch (_: Exception) {
        }
        // username/password as the last resort
        val username = Utils.settings!!.username
        val password = Utils.settings!!.password

        if (!(username.isNullOrEmpty() || password.isNullOrEmpty())) {
            val requestBytes = objMapper.writeValueAsBytes(
                TokenGenerateRequest(
                    username = username,
                    password = password,
                    expirationMinutes = Utils.settings!!.duration!!.minutes
                )
            )

            // block() is still available in Kotlin for Reactor types
            postGenerate(requestBytes)
                .timeout(Duration.ofSeconds(10))
                .block()
            return
        }
        throw Exception("Failed renewing token!")

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