package com.illiad.troad.service

import com.illiad.troad.model.Duration
import com.illiad.troad.service.security.Cryptos

data class Settings(
    val domain: String,
    val port: Int,
    val cacert: String,
    val crypto: Cryptos,
    val jwt: String?,
    val username: String?,
    val password: String?,
    val duration: Duration?,
)