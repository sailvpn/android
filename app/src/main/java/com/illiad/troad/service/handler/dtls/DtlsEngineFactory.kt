package com.illiad.troad.service.handler.dtls

import android.util.Log
import org.bouncycastle.jsse.BCSSLEngine
import org.bouncycastle.jsse.BCSSLParameters
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine

object DtlsEngineFactory {

    private const val TAG = "DTLS_ENGINE"

    /**
     * Creates and configures a DTLS SSLEngine using Bouncy Castle.
     * @param context The SSLContext initialized with "TLS" and "BCJSSE"
     * @param peerHost The hostname/IP of the remote peer
     * @param peerPort The port of the remote peer
     */
    fun createEngine(context: SSLContext, peerHost: String, peerPort: Int): SSLEngine {
        // 1. Create the base engine
        val engine = context.createSSLEngine(peerHost, peerPort)

        // 2. Configure Client/Server mode
        engine.useClientMode = true

        // 3. Use Bouncy Castle extensions to set DTLS-specific parameters
        if (engine is BCSSLEngine) {
            val bcParams: BCSSLParameters = engine.parameters

            // Force DTLS 1.2 (Standard for modern security)
            bcParams.protocols = arrayOf("DTLSv1.2")

            /**
             * CRITICAL: Set Maximum Packet Size (MTU)
             * Standard UDP MTU is 1500. We use 1200-1280 to account for
             * IP/UDP headers and potential tunneling (VPNs).
             */
            bcParams.maximumPacketSize = 1200

            // Apply the BC-specific parameters back to the engine
            engine.parameters = bcParams

            Log.d(TAG, "Configured BCSSLEngine for DTLSv1.2 with MTU 1200")
        } else {
            // This happens if the SSLContext wasn't created with the "BCJSSE" provider
            Log.e(TAG, "Engine is not a BCSSLEngine. DTLS features may be restricted.")
        }

        return engine
    }
}
