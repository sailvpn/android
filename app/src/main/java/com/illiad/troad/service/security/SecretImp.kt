package com.illiad.troad.service.security

import com.illiad.troad.Consts.MAX
import com.illiad.troad.Consts.MIN
import com.illiad.troad.Utils
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom


object SecretImp : Secret {
    private val secureRandom = SecureRandom()

    @get:Throws(NoSuchAlgorithmException::class)
    override val secret: ByteArray?
        get() {
            // Check if crypto type is JWT
            val cryptoType = Utils.settings?.crypto

            if (Cryptos.JWT2 == cryptoType || Cryptos.JWT == cryptoType) {
                // Get current token from token manager (may be dynamically renewed)
                val token = Utils.settings!!.jwt
                check(!(token == null || token.isEmpty())) { "Token is not configured." }
                return token.toByteArray(StandardCharsets.UTF_8)
            } else if (Cryptos.SHA_256 == cryptoType) {
                // Hash-based authentication
                val digest =
                    MessageDigest.getInstance(Cryptos.SHA_256.value)
                val secret = Utils.settings!!.secret
                check(!(secret == null || secret.isEmpty())) { "Secret is not configured." }
                return digest.digest(secret.toByteArray(StandardCharsets.UTF_8))
            }
            return null
        }

    override val cryptoType: Cryptos
        get() = Utils.settings!!.crypto

    override val cryptoTypeByte: Byte
        get() = Utils.settings!!.crypto.code

    override val cryptoLength: Short
        get() = Utils.settings!!.crypto.length

    override fun offset(): ByteArray {
        // generate random ran bytes of length params.min..params.max
        val offsetLen: Int = secureRandom.nextInt(MAX + MIN)
        val offsetBytes = ByteArray(offsetLen)
        secureRandom.nextBytes(offsetBytes)
        return offsetBytes
    }
}
