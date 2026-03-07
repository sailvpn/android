package com.illiad.troad.service

// 1. Declare your native interface (usually in a 'NativeEngine' object)
object NativeEngine {
    init {
        System.loadLibrary("tun2socks")
    }

    external fun startTun2Socks(
        fd: Int,
        proxyAddr: String,
        proxyPort: Int,
        mtu: Int,
        caCert: String,    // PEM formatted string
        header: String,
        sni: String = "",  // Base64 encoded header string
    ): Int

    external fun stopTun2Socks()
}