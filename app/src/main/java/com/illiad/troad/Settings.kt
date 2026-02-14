package com.illiad.troad

import com.illiad.troad.model.AutoRenew
import com.illiad.troad.model.Duration
import com.illiad.troad.service.security.Cryptos

data class Settings(
    val domain: String,
    val port: Int,
    val cacert: String,
    val crypto: Cryptos,
    val secret: String?,
    val jwt: String?,
    val username: String?,
    val password: String?,
    val duration: Duration?,
    val autoRenew: AutoRenew?
)
