package com.illiad.troad.service.security

import android.util.Log
import com.illiad.troad.Consts.CM
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*

object CertManager {
    @Volatile var sslCtx: SslContext? = null
    @Volatile var dtlsCtx: SSLContext? = null

    init {
        // 1. Force register BC providers at the top to override Android defaults
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        Security.removeProvider(BouncyCastleJsseProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleJsseProvider(), 2)
    }

    fun updateContext(cert: String) {
        synchronized(this) {
            val caBytes = cert.toByteArray(StandardCharsets.UTF_8)

            try {
                // 1. Prepare KeyStore using Bouncy Castle provider explicitly
                val ks = KeyStore.getInstance("PKCS12", "BC").apply {
                    load(null, null)
                }
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                    init(ks, "".toCharArray())
                }

                // 2. Initialize TLS Context (Netty)
                val caStreamForNetty = ByteArrayInputStream(caBytes)
                this.sslCtx = SslContextBuilder.forClient()
                    .keyManager(kmf)
                    .trustManager(caStreamForNetty)
                    .build()

                // 3. Initialize DTLS Context (Bouncy Castle JSSE)
                val caStreamForDtls = ByteArrayInputStream(caBytes)
                // IMPORTANT: Use "TLS" with "BCJSSE". It supports both TLS and DTLS.
                val context = SSLContext.getInstance("TLS", "BCJSSE")

                context.init(
                    kmf.keyManagers,
                    trustManagers(caStreamForDtls),
                    SecureRandom()
                )

                this.dtlsCtx = context

                Log.i(CM, "SSL and DTLS (BCJSSE) contexts initialized successfully.")
            } catch (e: Exception) {
                Log.e(CM, "Critical failure during Certificate initialization", e)
            }
        }
    }

    fun trustManagers(pem: InputStream): Array<TrustManager> {
        val cf = CertificateFactory.getInstance("X.509")
        val certificates = cf.generateCertificates(pem)

        val keyStore = KeyStore.getInstance("PKCS12", "BC").apply {
            load(null, null)
        }

        certificates.forEachIndexed { index, cert ->
            if (cert is X509Certificate) {
                keyStore.setCertificateEntry("ca_$index", cert)
            }
        }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        return tmf.trustManagers
    }
}
