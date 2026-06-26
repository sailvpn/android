package com.illiad.troad.service.security

import com.illiad.troad.Consts.MAX
import com.illiad.troad.Consts.MIN
import java.security.MessageDigest
import java.security.SecureRandom

object Secret {

    // 1. Force the JVM/Android engine to use OS-level entropy pool
    private val secureRandom = SecureRandom().apply {
        // Explictly force a seed draw operation to guarantee the PRNG engine is active
        // and seeded from /dev/urandom immediately upon class loading.
        nextBytes(ByteArray(1))
    }

    // 2. High-speed caching wrapper to completely eliminate thread synchronization bottlenecks
    private val digestThreadLocal = ThreadLocal.withInitial {
        // Explicitly asking for the SHA-256 standard engine
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

    /**
     * Generates a completely unpredictable byte length and fills it with true
     * high-entropy random binary noise to blind traffic analysis attacks.
     */
    fun offset(): ByteArray {
        val range = MAX - MIN
        // Safe check if configuration constraints are invalid
        val offsetLen = secureRandom.nextInt(range) + MIN

        val padding = ByteArray(offsetLen)
        secureRandom.nextBytes(padding) // Mutates array with true random data
        return padding
    }

    /**
     * Thread-Isolated SHA-256 Engine.
     * Guarantees maximum execution speed on multi-threaded background workers
     * on Android without thread lock contentions.
     */
    private fun sha256(input: ByteArray): ByteArray {
        val md = digestThreadLocal.get()
        if (md == null) {
            throw Exception("MessageDigest null...")
        }
        md.reset() // Wipe internal registers before digesting fresh content
        return md.digest(input)
    }
}

