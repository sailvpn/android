package com.illiad.troad.service.security

/**
 *
 * Pure Kotlin HeaderEncoder.
 * illiad header frame:
 * - 2 bytes: frame length (unsigned short)
 * - 2 bytes: token length (unsigned short)
 * - 1 byte: crypto type
 * - token bytes: variable
 * - random bytes: variable
 */
object HeaderEncoder {

    fun encode(crypto: Cryptos, secret: String?, token: String?): ByteArray? {
        // 1. Get secrets
        val secretBytes = Secret.secret(crypto, secret, token) ?: return null
        if (secretBytes.isEmpty()) {
            return null
        }
        val offset = Secret.offset()

        val secretLen = secretBytes.size
        val offsetLen = offset.size
        // 2 bytes (tokenLen) + 1 byte (typeInfo) + Token length + offset length
        val frameLen = 3 + secretLen + offsetLen

        // 3. Build the Raw Byte Array, Total size = 2 bytes (framelen) + frameLength
        val header = ByteArray(2 + frameLen)

        var pos = 0
        // Write Short frameLen (Big Endian)
        header[pos++] = (frameLen shr 8).toByte()
        header[pos++] = (frameLen and 0xFF).toByte()
        // Write Short secretLen (Big Endian)
        header[pos++] = (secretLen shr 8).toByte()
        header[pos++] = (secretLen and 0xFF).toByte()
        // Write Crypto Type
        header[pos++] = crypto.code.toByte()
        // Write Signature (Secret)
        secretBytes.copyInto(header, destinationOffset = pos)
        pos += secretLen
        // Write Offset
        offset.copyInto(header, destinationOffset = pos)

        return header
    }
}
