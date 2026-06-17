package com.illiad.troad.service

import com.illiad.troad.model.AutoRenew
import com.illiad.troad.model.Duration
import com.illiad.troad.service.security.Cryptos

data class Settings(
    val domain: String,
    val sni: String,
    val port: Int,
    val cacert: String?,
    val crypto: Cryptos,
    val jwt: String?, // Stores JWT or JWT2 string
    val autoRenew: AutoRenew?,
    val duration: Duration?,
    val secret: String?       // Stores the SHA-256 key
) {
    val isValid: Boolean
        get() = domain.isNotBlank() && sni.isNotBlank() && port in 1..65535 && !cacert.isNullOrBlank()
                && (crypto == Cryptos.JWT || crypto == Cryptos.JWT2 || crypto == Cryptos.SHA_256)

    /**
     * Extracts the exact security key or token string needed for the VPN
     * handshake based on the active crypto configuration.
     */
    val authKey: String?
        get() = when (crypto) {
            Cryptos.JWT, Cryptos.JWT2 -> jwt
            Cryptos.SHA_256 -> secret
            else -> null
        }

    /**
     * Checks if the active crypto mode requires authentication credentials.
     */
    val requiresAuthKey: Boolean
        get() = crypto == Cryptos.JWT || crypto == Cryptos.JWT2 || crypto == Cryptos.SHA_256
}

