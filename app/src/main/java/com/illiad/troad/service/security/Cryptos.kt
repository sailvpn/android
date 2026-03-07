package com.illiad.troad.service.security

/**
 * Pure Kotlin Cryptos Enum.
 * Removed Java Optional and JvmStatic for KMP compatibility.
 */
enum class Cryptos(val value: String, val code: Int, val length: Int) {
    JWT2("JWT2", 0x09, 0),
    JWT("JWT", 0x07, 0),
    SHA_224("SHA-224", 0x10, 28),
    SHA_256("SHA-256", 0x20, 32),
    SHA_384("SHA-384", 0x30, 48),
    SHA_512("SHA-512", 0x40, 64),
    SHA_512_224("SHA-512/224", 0x50, 28),
    SHA_512_256("SHA-512/256", 0x60, 32),
    HMAC_SHA224("HmacSHA224", 0x70, 28),
    HMAC_SHA256("HmacSHA256", 0x80, 32),
    HMAC_SHA384("HmacSHA384", 0x90, 48),
    HMAC_SHA512("HmacSHA512", 0xA0, 64),
    SHA224_WITH_RSA("SHA224withRSA", 0xB0, 0),
    SHA256_WITH_RSA("SHA256withRSA", 0xC0, 0),
    SHA384_WITH_RSA("SHA384withRSA", 0xD0, 0),
    SHA512_WITH_RSA("SHA512withRSA", 0xE0, 0),
    SHA224_WITH_DSA("SHA224withDSA", 0xF0, 0),
    SHA256_WITH_DSA("SHA256withDSA", 0x01, 0),
    SHA384_WITH_DSA("SHA384withDSA", 0x11, 0),
    SHA512_WITH_DSA("SHA512withDSA", 0x21, 0),
    SHA224_WITH_ECDSA("SHA224withECDSA", 0x31, 0),
    SHA256_WITH_ECDSA("SHA256withECDSA", 0x41, 0),
    SHA384_WITH_ECDSA("SHA384withECDSA", 0x51, 0),
    SHA512_WITH_ECDSA("SHA512withECDSA", 0x61, 0),
    SHA3_224("SHA3-224", 0x71, 28),
    SHA3_256("SHA3-256", 0x81, 32),
    SHA3_384("SHA3-384", 0x91, 48),
    SHA3_512("SHA3-512", 0xA1, 64),
    HMAC_SHA3_224("HmacSHA3-224", 0xB1, 28),
    HMAC_SHA3_256("HmacSHA3-256", 0xC1, 32),
    HMAC_SHA3_384("HmacSHA3-384", 0xD1, 48),
    HMAC_SHA3_512("HmacSHA3-512", 0xE1, 64),
    NONE("NONE", 0x00, 0);

    companion object {
        val AvailableCryptos = listOf(JWT, JWT2, SHA_256)

        /**
         * Replaces Optional<Cryptos>.
         * Returns null if the value doesn't match any enum entry.
         */
        fun fromValue(value: String?): Cryptos? {
            if (value == null) return null
            return entries.find { it.value == value }
        }

        fun fromCode(code: Int): Cryptos? =
            entries.find { it.code == code }
    }
}

