package com.illiad.troad.service.security

import com.illiad.troad.Consts.MAX
import com.illiad.troad.Consts.MIN
import java.security.MessageDigest
import java.security.SecureRandom

object Secret {

    // FIX: Mandate True Cryptographically Secure RNG for network defense
    private val secureRandom = SecureRandom()

    // Optimization: ThreadLocal prevents global locking bottlenecks in high-concurrency loops
    private val digestThreadLocal = ThreadLocal.withInitial {
        MessageDigest.getInstance("SHA-256")
    }

    fun secret(crypto: Cryptos, secret: String?, token: String?): ByteArray? {
        return try {
            when (crypto) {
                Cryptos.JWT, Cryptos.JWT2 -> {
                    if (token.isNullOrEmpty()) return null
                    token.encodeToByteArray()
                }

                Cryptos.SHA_256 -> {
                    if (secret.isNullOrEmpty()) return null
                    sha256(secret.encodeToByteArray())
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun offset(): ByteArray {
        // Compute length boundary limits securely
        val range = MAX - MIN
        val offsetLen = secureRandom.nextInt(range) + MIN

        // Populate array with true high-entropy cryptographic noise
        val padding = ByteArray(offsetLen)
        secureRandom.nextBytes(padding)
        return padding
    }

    /**
     * Highly Optimized Thread-Safe SHA-256 helper.
     */
    private fun sha256(input: ByteArray): ByteArray {
        val md = digestThreadLocal.get()
        if (md == null) {
            throw Exception("MessageDigest null")
        }
        md.reset() // Wipe state clean before digest reuse
        return md.digest(input)
    }
}
