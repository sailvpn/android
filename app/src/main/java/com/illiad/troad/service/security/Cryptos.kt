package com.illiad.troad.service.security

/**
 * Pure Kotlin Cryptos Enum.
 * Removed Java Optional and JvmStatic for KMP compatibility.
 */
enum class Cryptos(val value: String, val code: Int, val length: Int) {
    JWT2("JWT2", 0x81, 0),
    JWT("JWT", 0x82, 0),
    SHA_256("SHA-256", 0x83, 32);

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

