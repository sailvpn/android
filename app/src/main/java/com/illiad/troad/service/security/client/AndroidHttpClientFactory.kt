package com.illiad.troad.service.security.client

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class AndroidHttpClientFactory : HttpClientFactory {

    override fun createSecureClient(cacertPem: String?): HttpClient {
        return HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 10000
            }

            // Secure out-of-band certificate context mapping
            if (!cacertPem.isNullOrBlank()) {
                engine {
                    sslManager = { httpsURLConnection ->
                        val certFactory = CertificateFactory.getInstance("X.509")
                        val certInputStream = ByteArrayInputStream(cacertPem.toByteArray(Charsets.UTF_8))
                        val certificate = certFactory.generateCertificate(certInputStream)

                        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                            load(null, null)
                            setCertificateEntry("ca_root", certificate)
                        }

                        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                            init(keyStore)
                        }

                        val sslContext = SSLContext.getInstance("TLS").apply {
                            init(null, trustManagerFactory.trustManagers, java.security.SecureRandom())
                        }

                        httpsURLConnection.sslSocketFactory = sslContext.socketFactory
                    }
                }
            }
        }
    }
}
