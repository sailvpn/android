package com.illiad.troad.service.security

/**
 *
 * Pure Kotlin HeaderEncoder.
 * Generates a URL-Safe Base64 String containing:
 * [2 bytes Length][1 byte CryptoType][Signature][Offset][2 bytes CRLF]
 *
 * if the encryption returns a fixed-length signature, the length field contains the whole length(length + cryptoType + signature + offset CRLF).
 * if the encryption returns a variable-length signature, the length field contain the length of the signature only(length + cryptoType + signature).
 */
object HeaderEncoder {

    private val CRLF = byteArrayOf(0x0D, 0x0A)
    fun encode(crypto: Cryptos, secret: String?, token: String?): ByteArray? {
        // 1. Get secrets from your implementation
        val secretBytes = Secret.secret(crypto, secret, token) ?: return null
        val offset = Secret.offset()
        val signLength = crypto.length

        // 2. Calculate the "Header Length" field based on your protocol logic
        val headerLengthField: Int = if (signLength > 0) {
            // Fixed length: (Length(2) + Type(1) + Sign + Offset + CRLF(2)) = signLength + offset.size + 5
            signLength + offset.size + 5
        } else {
            // Variable length: (Length(2) + Type(1) + Sign) = secretBytes.size + 3
            secretBytes.size + 3
        }

        // 3. Build the Raw Byte Array
        // Total size = 2 (length) + 1 (type) + signature + offset + 2 (CRLF)
        val totalSize = 2 + 1 + secretBytes.size + offset.size + 2
        val header = ByteArray(totalSize)

        var pos = 0
        // Write Short (Big Endian)
        header[pos++] = (headerLengthField shr 8).toByte()
        header[pos++] = (headerLengthField and 0xFF).toByte()

        // Write Crypto Type
        header[pos++] = crypto.code.toByte()

        // Write Signature (Secret)
        secretBytes.copyInto(header, destinationOffset = pos)
        pos += secretBytes.size

        // Write Offset
        offset.copyInto(header, destinationOffset = pos)
        pos += offset.size

        // Write CRLF
        CRLF.copyInto(header, destinationOffset = pos)

        return header
    }
}
