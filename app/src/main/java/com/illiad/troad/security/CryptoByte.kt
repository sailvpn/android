package com.illiad.troad.security

class CryptoByte {
    companion object {
        fun toByte(c: Cryptos): Byte {
            when (c) {
                Cryptos.SHA_224 -> return 0x10.toByte()
                Cryptos.SHA_256 -> return 0x20.toByte()
                Cryptos.SHA_384 -> return 0x30.toByte()
                Cryptos.SHA_512 -> return 0x40.toByte()
                Cryptos.SHA_512_224 -> return 0x50.toByte()
                Cryptos.SHA_512_256 -> return 0x60.toByte()
                Cryptos.HMAC_SHA224 -> return 0x70.toByte()
                Cryptos.HMAC_SHA256 -> return 0x80.toByte()
                Cryptos.HMAC_SHA384 -> return 0x90.toByte()
                Cryptos.HMAC_SHA512 -> return 0xA0.toByte()
                Cryptos.SHA224_WITH_RSA -> return 0xB0.toByte()
                Cryptos.SHA256_WITH_RSA -> return 0xC0.toByte()
                Cryptos.SHA384_WITH_RSA -> return 0xD0.toByte()
                Cryptos.SHA512_WITH_RSA -> return 0xE0.toByte()
                Cryptos.SHA224_WITH_DSA -> return 0xF0.toByte()
                Cryptos.SHA256_WITH_DSA -> return 0x01.toByte()
                Cryptos.SHA384_WITH_DSA -> return 0x11.toByte()
                Cryptos.SHA512_WITH_DSA -> return 0x21.toByte()
                Cryptos.SHA224_WITH_ECDSA -> return 0x31.toByte()
                Cryptos.SHA256_WITH_ECDSA -> return 0x41.toByte()
                Cryptos.SHA384_WITH_ECDSA -> return 0x51.toByte()
                Cryptos.SHA512_WITH_ECDSA -> return 0x61.toByte()
                Cryptos.SHA3_224 -> return 0x71.toByte()
                Cryptos.SHA3_256 -> return 0x81.toByte()
                Cryptos.SHA3_384 -> return 0x91.toByte()
                Cryptos.SHA3_512 -> return 0xA1.toByte()
                Cryptos.HMAC_SHA3_224 -> return 0xB1.toByte()
                Cryptos.HMAC_SHA3_256 -> return 0xC1.toByte()
                Cryptos.HMAC_SHA3_384 -> return 0xD1.toByte()
                Cryptos.HMAC_SHA3_512 -> return 0xE1.toByte()
                else -> throw IllegalArgumentException("Unknown Cryptos standard: " + c)
            }
        }

        fun toCrypto(b: Byte): Cryptos {
            when (b) {
                0x10.toByte() -> return Cryptos.SHA_224
                0x20.toByte() -> return Cryptos.SHA_256
                0x30.toByte() -> return Cryptos.SHA_384
                0x40.toByte() -> return Cryptos.SHA_512
                0x50.toByte() -> return Cryptos.SHA_512_224
                0x60.toByte() -> return Cryptos.SHA_512_256
                0x70.toByte() -> return Cryptos.HMAC_SHA224
                0x80.toByte() -> return Cryptos.HMAC_SHA256
                0x90.toByte() -> return Cryptos.HMAC_SHA384
                0xA0.toByte() -> return Cryptos.HMAC_SHA512
                0xB0.toByte() -> return Cryptos.SHA224_WITH_RSA
                0xC0.toByte() -> return Cryptos.SHA256_WITH_RSA
                0xD0.toByte() -> return Cryptos.SHA384_WITH_RSA
                0xE0.toByte() -> return Cryptos.SHA512_WITH_RSA
                0xF0.toByte() -> return Cryptos.SHA224_WITH_DSA
                0x01.toByte() -> return Cryptos.SHA256_WITH_DSA
                0x11.toByte() -> return Cryptos.SHA384_WITH_DSA
                0x21.toByte() -> return Cryptos.SHA512_WITH_DSA
                0x31.toByte() -> return Cryptos.SHA224_WITH_ECDSA
                0x41.toByte() -> return Cryptos.SHA256_WITH_ECDSA
                0x51.toByte() -> return Cryptos.SHA384_WITH_ECDSA
                0x61.toByte() -> return Cryptos.SHA512_WITH_ECDSA
                0x71.toByte() -> return Cryptos.SHA3_224
                0x81.toByte() -> return Cryptos.SHA3_256
                0x91.toByte() -> return Cryptos.SHA3_384
                0xA1.toByte() -> return Cryptos.SHA3_512
                0xB1.toByte() -> return Cryptos.HMAC_SHA3_224
                0xC1.toByte() -> return Cryptos.HMAC_SHA3_256
                0xD1.toByte() -> return Cryptos.HMAC_SHA3_384
                0xE1.toByte() -> return Cryptos.HMAC_SHA3_512
                else -> throw IllegalArgumentException("Unknown byte value: " + b)
            }
        }

        fun byteLength(c: Cryptos): Short {
            when (c) {
                Cryptos.SHA_224, Cryptos.HMAC_SHA224, Cryptos.SHA3_224, Cryptos.HMAC_SHA3_224 -> return 28 // 224 bits = 28 bytes

                Cryptos.SHA_256, Cryptos.HMAC_SHA256, Cryptos.SHA3_256, Cryptos.HMAC_SHA3_256 -> return 32 // 256 bits = 32 bytes

                Cryptos.SHA_384, Cryptos.HMAC_SHA384, Cryptos.SHA3_384, Cryptos.HMAC_SHA3_384 -> return 48 // 384 bits = 48 bytes

                Cryptos.SHA_512, Cryptos.HMAC_SHA512, Cryptos.SHA3_512, Cryptos.HMAC_SHA3_512 -> return 64 // 512 bits = 64 bytes

                Cryptos.SHA_512_224 -> return 28 // 224 bits = 28 bytes

                Cryptos.SHA_512_256 -> return 32 // 256 bits = 32 bytes

                Cryptos.SHA224_WITH_RSA, Cryptos.SHA224_WITH_DSA, Cryptos.SHA224_WITH_ECDSA, Cryptos.SHA256_WITH_RSA, Cryptos.SHA256_WITH_DSA, Cryptos.SHA256_WITH_ECDSA, Cryptos.SHA384_WITH_RSA, Cryptos.SHA384_WITH_DSA, Cryptos.SHA384_WITH_ECDSA, Cryptos.SHA512_WITH_RSA, Cryptos.SHA512_WITH_DSA, Cryptos.SHA512_WITH_ECDSA -> return 0 // return 0 for variable length signatures

                else -> throw IllegalArgumentException("Unknown Cryptos standard: " + c)
            }
        }
    }
}