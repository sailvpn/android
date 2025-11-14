package com.illiad.troad.service.security

import android.content.Context
import com.illiad.troad.R
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object Ssl {
    // Configure SSL.
    var sslCtx: SslContext? = null
        private set // Make the setter private so it can only be set from within this object

    // A flag to ensure initialization happens only once
    private var isInitialized = false
    private val lock = Any()

    /**
     * Initializes the SslContext. This must be called once, typically from the Application
     * or Service class, before the SslContext is used.
     * @param context An Android Context, preferably the application context.
     */
    fun initialize(context: Context) {
        synchronized(lock) {
            if (isInitialized) {
                return // Already initialized
            }

            // Use the provided context to get resources
            val trustStorePassword = context.getString(R.string.trust_store_password)
            val trustStoreType = context.getString(R.string.trust_store_type)

            // Load the trust store from res/raw
            val ts = KeyStore.getInstance(trustStoreType)
            context.resources.openRawResource(R.raw.server).use { inputStream ->
                ts.load(inputStream, trustStorePassword.toCharArray())
            }

            // Initialize TrustManagerFactory with the trust store
            val trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(ts)

            // Build the SslContext with the TrustManagerFactory
            sslCtx = SslContextBuilder.forClient()
                .trustManager(trustManagerFactory)
                .build()

            isInitialized = true
        }
    }

}