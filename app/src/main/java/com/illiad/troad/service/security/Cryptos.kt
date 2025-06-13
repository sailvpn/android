package com.illiad.troad.service.security

enum class Cryptos(value: String) {
    SHA_224("SHA-224"),
    SHA_256("SHA-256"),
    SHA_384("SHA-384"),
    SHA_512("SHA-512"),
    SHA_512_224("SHA-512/224"),
    SHA_512_256("SHA-512/256"),
    HMAC_SHA224("HmacSHA224"),
    HMAC_SHA256("HmacSHA256"),
    HMAC_SHA384("HmacSHA384"),
    HMAC_SHA512("HmacSHA512"),
    SHA224_WITH_RSA("SHA224withRSA"),
    SHA256_WITH_RSA("SHA256withRSA"),
    SHA384_WITH_RSA("SHA384withRSA"),
    SHA512_WITH_RSA("SHA512withRSA"),
    SHA224_WITH_DSA("SHA224withDSA"),
    SHA256_WITH_DSA("SHA256withDSA"),
    SHA384_WITH_DSA("SHA384withDSA"),
    SHA512_WITH_DSA("SHA512withDSA"),
    SHA224_WITH_ECDSA("SHA224withECDSA"),
    SHA256_WITH_ECDSA("SHA256withECDSA"),
    SHA384_WITH_ECDSA("SHA384withECDSA"),
    SHA512_WITH_ECDSA("SHA512withECDSA"),
    SHA3_224("SHA3-224"),
    SHA3_256("SHA3-256"),
    SHA3_384("SHA3-384"),
    SHA3_512("SHA3-512"),
    HMAC_SHA3_224("HmacSHA3-224"),
    HMAC_SHA3_256("HmacSHA3-256"),
    HMAC_SHA3_384("HmacSHA3-384"),
    HMAC_SHA3_512("HmacSHA3-512");

    val value: String?

    init {
        this.value = value
    }
}