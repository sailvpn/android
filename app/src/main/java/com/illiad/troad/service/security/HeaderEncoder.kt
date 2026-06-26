package com.illiad.troad.service.security

/**
 * Pure Kotlin HeaderEncoder.
 * illiad header frame:
 * - 2 bytes: frame length (unsigned short payload size following this field)
 * - 2 bytes: token length (unsigned short)
 * - 1 byte: crypto type
 * - token bytes: variable
 * - random bytes: variable
 */
object HeaderEncoder {

    // Hard ceiling for standard unsigned short network parameters (2^16 - 1)
    private const val MAX_UNSIGNED_SHORT = 65535

    fun encode(crypto: Cryptos, secret: String?, token: String?): ByteArray? {
        // 1. Retrieve the cryptographically wrapped token secret block
        val secretBytes = Secret.secret(crypto, secret, token) ?: return null
        if (secretBytes.isEmpty()) {
            return null
        }

        val offset = Secret.offset()
        val secretLen = secretBytes.size
        val offsetLen = offset.size

        // Calculate payload size: 2 bytes (tokenLen) + 1 byte (crypto type) + data strings
        val frameLen = 2 + 1 + secretLen + offsetLen

        // FAIL-SAFE: Guard against integer overflow on the 2-byte unsigned short wire header
        if (frameLen > MAX_UNSIGNED_SHORT) {
            throw IllegalArgumentException("Combined Illiad frame payload size ($frameLen) exceeds the 16-bit network buffer boundary.")
        }

        // 2. Allocate the continuous target array wrapper: 2 bytes (frameLen header) + actual frame payload
        val header = ByteArray(2 + frameLen)

        var pos = 0

        // Write Short frameLen (Big Endian serialization)
        header[pos++] = (frameLen ushr 8).toByte()
        header[pos++] = (frameLen and 0xFF).toByte()

        // Write Short secretLen (Big Endian serialization)
        header[pos++] = (secretLen ushr 8).toByte()
        header[pos++] = (secretLen and 0xFF).toByte()

        // Write Crypto Type byte indicator flag
        header[pos++] = (crypto.code and 0xFF).toByte()

        // Copy the token binary payload array into the main buffer stream
        secretBytes.copyInto(header, destinationOffset = pos)
        pos += secretLen

        // Append the trailing high-entropy random padding block
        offset.copyInto(header, destinationOffset = pos)

        return header
    }
}
