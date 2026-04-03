package com.illiad.troad.service.security

import com.illiad.troad.Consts.MAX
import com.illiad.troad.Consts.MIN
import kotlin.random.Random

object Secret {

    // Use Kotlin's Random.Default (uses SecureRandom under the hood on JVM/Android)
    private val random = Random.Default

    fun secret(crypto: Cryptos, secret: String, token: String): ByteArray? {
        return when (crypto) {
            Cryptos.JWT, Cryptos.JWT2 -> {
                require(token.isNotEmpty()) { "Token is not configured." }
                token.encodeToByteArray()
            }

            Cryptos.SHA_256 -> {
                require(secret.isNotEmpty()) { "Secret is not configured." }
                // Replaces MessageDigest.getInstance("SHA-256")
                sha256(secret.encodeToByteArray())
            }

            else -> null
        }
    }

    fun offset(): ByteArray {
        // Kotlin Random range: nextInt(min, max)
        val offsetLen = random.nextInt(MIN, MAX)
        return random.nextBytes(offsetLen)
    }

    /**
     * Cross-platform SHA-256 helper.
     * On Android, this still maps to MessageDigest.
     * On iOS/Native, it maps to CommonCrypto.
     */
    private fun sha256(input: ByteArray): ByteArray {
        // If sticking to Android-only for now:
        return java.security.MessageDigest.getInstance("SHA-256").digest(input)

        // Note: For true KMP, move this to an 'expect' function
        // and implement with CommonCrypto on iOS.
    }
}
