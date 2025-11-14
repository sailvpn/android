package com.illiad.troad.service.security

import android.content.Context
import android.util.Log
import com.illiad.troad.R
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory

object Dtls {
    // This will hold the configured DTLS context for Netty.
    var dtlsCtx: SslContext? = null // <-- Correct type is io.netty.handler.ssl.SslContext
        private set // Make the setter private so it can only be set from within this object.

    // A flag to ensure initialization happens only once.
    private var isInitialized = false
    private val lock = Any()

    /**
     * Initializes the SslContext for DTLS. This must be called once,
     * preferably from an Application or Service class, before the context is used.
     *
     * @param context An Android Context, preferably the application context.
     */
    fun initialize(context: Context) {
        synchronized(lock) {
            if (isInitialized) {
                return // Already initialized
            }

            try {
                // 1. Get configuration from string resources.
                val keyStorePassword = context.getString(R.string.trust_store_password)
                val keyStoreType =
                    context.getString(R.string.trust_store_type) // e.g., "BKS" or "PKCS12"

                // 2. Load the KeyStore (for client identity).
                // The KeyStore should contain your client's certificate and private key.
                val ks = KeyStore.getInstance(keyStoreType)
                // IMPORTANT: Use a specific client keystore file, not the server one.
                context.resources.openRawResource(R.raw.server).use { inputStream ->
                    ks.load(inputStream, keyStorePassword.toCharArray())
                }

                // 3. Initialize a KeyManagerFactory with your client's key.
                // This is used for client authentication (proving who you are to the server).
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                kmf.init(ks, keyStorePassword.toCharArray())

                // 4. Load the TrustStore (to verify the server).
                // The TrustStore should contain the server's CA certificate.
                val ts = KeyStore.getInstance(keyStoreType)
                // IMPORTANT: Use a specific server truststore file.
                context.resources.openRawResource(R.raw.server).use { inputStream ->
                    ts.load(inputStream, keyStorePassword.toCharArray())
                }

                // 5. Initialize a TrustManagerFactory with the server's certificate.
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(ts)

                // 6. Build the Netty SslContext. Netty will automatically use DTLS
                //    when this context is used with a DatagramChannel.
                dtlsCtx = SslContextBuilder.forClient()
                    .keyManager(kmf)         // Identifies this client
                    .trustManager(tmf)       // Verifies the server
                    .build()                 // Build the SslContext object

                isInitialized = true
                Log.i("Dtls", "DTLS context initialized successfully.")

            } catch (e: Exception) {
                // Log the error. Initialization failed, dtlsCtx will remain null.
                Log.e("Dtls", "Failed to initialize DTLS context", e)
            }
        }
    }
}





