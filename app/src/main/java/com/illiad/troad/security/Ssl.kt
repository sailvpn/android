package com.illiad.troad.security

import android.content.res.Resources
import com.illiad.troad.R
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

object Ssl {
    // Configure SSL.
    var sslCtx: SslContext? = null

    init {
        // Load the trust store
        val ts = KeyStore.getInstance(Resources.getSystem().getString(R.string.trust_store_type))
        val trustStoreFile = File(Resources.getSystem().getString(R.string.trust_store))
        FileInputStream(trustStoreFile).use { `is` ->
            ts.load(
                `is`,
                Resources.getSystem().getString(R.string.trust_store_password).toCharArray()
            )
        }
        // Initialize TrustManagerFactory with the trust store
        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(ts)

        // Build the SslContext with the TrustManagerFactory
        this.sslCtx = SslContextBuilder.forClient().trustManager(trustManagerFactory).build()
    }
}
