package com.illiad.troad.service.security

import io.netty.handler.ssl.SslContext
import java.io.InputStream

import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory


object CertManager {

    @Volatile
    var sslCtx: SslContext? = null

    @Volatile
    var dtlsCtx: SSLContext? = null

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