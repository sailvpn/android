package com.illiad.troad.service.security

import java.util.Optional
import kotlin.Short
import kotlin.String
import kotlin.plus

enum class Cryptos(value: String, code: Byte, length: Short) {
    JWT2("JWT2", 0x09.toByte(), 0.toShort()),
    JWT("JWT", 0x07.toByte(), 0.toShort()),
    SHA_224("SHA-224", 0x10.toByte(), 28.toShort()),
    SHA_256("SHA-256", 0x20.toByte(), 32.toShort()),
    SHA_384("SHA-384", 0x30.toByte(), 48.toShort()),
    SHA_512("SHA-512", 0x40.toByte(), 64.toShort()),
    SHA_512_224("SHA-512/224", 0x50.toByte(), 28.toShort()),
    SHA_512_256("SHA-512/256", 0x60.toByte(), 32.toShort()),
    HMAC_SHA224("HmacSHA224", 0x70.toByte(), 28.toShort()),
    HMAC_SHA256("HmacSHA256", 0x80.toByte(), 32.toShort()),
    HMAC_SHA384("HmacSHA384", 0x90.toByte(), 48.toShort()),
    HMAC_SHA512("HmacSHA512", 0xA0.toByte(), 64.toShort()),
    SHA224_WITH_RSA("SHA224withRSA", 0xB0.toByte(), 0.toShort()),
    SHA256_WITH_RSA("SHA256withRSA", 0xC0.toByte(), 0.toShort()),
    SHA384_WITH_RSA("SHA384withRSA", 0xD0.toByte(), 0.toShort()),
    SHA512_WITH_RSA("SHA512withRSA", 0xE0.toByte(), 0.toShort()),
    SHA224_WITH_DSA("SHA224withDSA", 0xF0.toByte(), 0.toShort()),
    SHA256_WITH_DSA("SHA256withDSA", 0x01.toByte(), 0.toShort()),
    SHA384_WITH_DSA("SHA384withDSA", 0x11.toByte(), 0.toShort()),
    SHA512_WITH_DSA("SHA512withDSA", 0x21.toByte(), 0.toShort()),
    SHA224_WITH_ECDSA("SHA224withECDSA", 0x31.toByte(), 0.toShort()),
    SHA256_WITH_ECDSA("SHA256withECDSA", 0x41.toByte(), 0.toShort()),
    SHA384_WITH_ECDSA("SHA384withECDSA", 0x51.toByte(), 0.toShort()),
    SHA512_WITH_ECDSA("SHA512withECDSA", 0x61.toByte(), 0.toShort()),
    SHA3_224("SHA3-224", 0x71.toByte(), 28.toShort()),
    SHA3_256("SHA3-256", 0x81.toByte(), 32.toShort()),
    SHA3_384("SHA3-384", 0x91.toByte(), 48.toShort()),
    SHA3_512("SHA3-512", 0xA1.toByte(), 64.toShort()),
    HMAC_SHA3_224("HmacSHA3-224", 0xB1.toByte(), 28.toShort()),
    HMAC_SHA3_256("HmacSHA3-256", 0xC1.toByte(), 32.toShort()),
    HMAC_SHA3_384("HmacSHA3-384", 0xD1.toByte(), 48.toShort()),
    HMAC_SHA3_512("HmacSHA3-512", 0xE1.toByte(), 64.toShort());

    val value: String
    val code: Byte
    val length: Short

    init {
        this.value = value
        this.code = code
        this.length = length
    }

    companion object {
        // Define the specific subset for settings screen
        val AvailableCryptos = listOf(JWT, JWT2, SHA_256)

        fun fromValue(value: String?): Optional<Cryptos> {
            if (value == null) return Optional.empty()

            return Optional.ofNullable(Cryptos.entries.find { it.value == value })
        }
    }
}