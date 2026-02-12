package com.illiad.troad.service

import com.illiad.troad.service.security.Cryptos

data class VpnSettings(
    val domain: String,
    val port: Int,
    val cacert: String,
    val crypto: Cryptos,
    val secret: String?,
    val jwt: String?
)
