package com.illiad.troad.service.security

import com.illiad.troad.R
import com.illiad.troad.service.Utils.getString
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object Ssl {
    // Configure SSL.
    var sslCtx: SslContext? = null

    init {
        // Load the trust store
        val ts = KeyStore.getInstance(getString(R.string.trust_store_type))
        val trustStoreFile = File(getString(R.string.trust_store))
        FileInputStream(trustStoreFile).use { fis ->
            ts.load(
                fis, getString(R.string.trust_store_password).toCharArray()
            )
        }
        // Initialize TrustManagerFactory with the trust store
        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(ts)

        // Build the SslContext with the TrustManagerFactory
        this.sslCtx = SslContextBuilder.forClient().trustManager(trustManagerFactory).build()
    }

    // this is for testing purpose, you can create a custom TrustManager (more verbose)
    fun createInsecureSslContext(): SslContext {
        val trustAllCerts = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                // No-op: Trust all client certificates
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                // No-op: Trust all server certificates
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf() // Return an empty array
            }
        }

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        // Note: This manual initialization with a custom manager is a bit more involved.
        // For simplicity, Netty's InsecureTrustManagerFactory.INSTANCE is usually preferred for this specific "trust all" scenario.
        // However, knowing how to create a custom one can be useful for other scenarios.
        // For "trust all", you'd typically initialize the TrustManagerFactory with null or a custom KeyStore
        // that effectively trusts everything, but for a simple "trust all" X509TrustManager,
        // directly providing it to SslContextBuilder is cleaner if the builder supports it directly,
        // or using InsecureTrustManagerFactory as shown above.

        // A more direct way with SslContextBuilder if you have your custom X509TrustManager:
        return SslContextBuilder.forClient()
            .trustManager(trustAllCerts) // Directly use your custom trust manager
            .build()
    }


// --- How to use it in your ConnectionHandler (example) ---
// Assuming you have the createInsecureSslContextForClient() function available

// ... inside your ConnectionHandler class ...
// public override fun channelRead0(ctx: ChannelHandlerContext, connection: Connection) {
// ...
// .connect(serverDomain, serverPort)
// .addListener(ChannelFutureListener { future: ChannelFuture? ->
//     if (future!!.isSuccess) {
//         val ch = future.channel()

//         // *** Replace this part ***
//         // Original:
//         // val sslHandler: SslHandler = Ssl.sslCtx!!.newHandler(
//         //     ch.alloc(),
//         //     serverDomain,
//         //     serverPort
//         // )

//         // *** With this (for testing/development ONLY) ***
//         val insecureSslCtx = createInsecureSslContextForClient() // Get the insecure context
//         val sslHandler: SslHandler = insecureSslCtx.newHandler(
//             ch.alloc(),
//             serverDomain, // Still good practice to provide host/port for SNI if server uses it
//             serverPort
//         )

//         val pipeline = ch.pipeline()
//         pipeline.addLast(sslHandler) // Add the SSL handler first
//         // ... rest of your SSL handshake and pipeline setup
//     }
//     // ...

}
