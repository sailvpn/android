package com.illiad.troad.service.security

import android.util.Log
import com.illiad.troad.Consts.CM
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory


object CertManager {

    @Volatile
    var sslCtx: SslContext? = null

    @Volatile
    var dtlsCtx: SSLContext? = null

    fun updateContext(cert: String) {
        synchronized(this) {
            val ca =
                ByteArrayInputStream(cert.toByteArray(StandardCharsets.UTF_8))
            try {

                // 1. Prepare empty Client Identity (only required for Client Auth)
                val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(
                        null,
                        null
                    ) // Passing null for stream and password creates an empty in-memory store
                }

                val kmf =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                        .apply {
                            init(ks, "".toCharArray())
                        }

                // 2. Initialize TLS Context (SSL)
                CertManager.sslCtx = SslContextBuilder.forClient()
                    .keyManager(kmf)
                    .trustManager(ca) // Netty handles PEM InputStreams directly
                    .build()


                // 3. Initialize DTLS Context
                // Re-open stream for the second context
                ca.reset()
                // Build JSSE SSLContext for DTLS
                dtlsCtx = SSLContext.getInstance("DTLS")
                dtlsCtx!!.init(
                    kmf.keyManagers,
                    trustManagers(ca),
                    SecureRandom()
                )
                ca.close()
                Log.i(
                    CM,
                    "Certificates and SSL/DTLS contexts initialized successfully."
                )
            } catch (e: Exception) {
                Log.e(CM, "Critical failure during Certificate initialization", e)
            }
        }
    }



    fun trustManagers(pem: InputStream): Array<TrustManager> {
        // 1. Use generateCertificates to parse the entire collection
        val cf = CertificateFactory.getInstance("X.509")
        val certificates = cf.generateCertificates(pem) // Returns Collection<out Certificate>

        // 2. Initialize empty in-memory KeyStore
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
        }

        // 3. Loop through all certs and add to KeyStore with unique aliases
        certificates.forEachIndexed { index, cert ->
            if (cert is X509Certificate) {
                keyStore.setCertificateEntry("ca_$index", cert)
            }
        }

        // 4. Create and return the TrustManagers
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        return tmf.trustManagers
    }
}