package com.illiad.troad.service.security

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 *
 * Pure Kotlin HeaderEncoder.
 * Generates a URL-Safe Base64 String containing:
 * [2 bytes Length][1 byte CryptoType][Signature][Offset][2 bytes CRLF]
 *
 * if the encryption returns a fixed-length signature, the length field contains the whole length(length + cryptoType + signature + offset CRLF).
 * if the encryption returns a variable-length signature, the length field contain the length of the signature only(length + cryptoType + signature).
 */
@OptIn(ExperimentalEncodingApi::class)
object HeaderEncoder {

    private val CRLF = byteArrayOf(0x0D, 0x0A)
    fun encodeHeader(crypto: Cryptos, secret: String, token: String): String {
        // 1. Get secrets from your implementation
        val secretBytes = Secret.secret(crypto, secret, token)
            ?: throw IllegalStateException("Secret not initialized")
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
        val rawHeader = ByteArray(totalSize)

        var pos = 0
        // Write Short (Big Endian)
        rawHeader[pos++] = (headerLengthField shr 8).toByte()
        rawHeader[pos++] = (headerLengthField and 0xFF).toByte()

        // Write Crypto Type
        rawHeader[pos++] = crypto.code.toByte()

        // Write Signature (Secret)
        secretBytes.copyInto(rawHeader, destinationOffset = pos)
        pos += secretBytes.size

        // Write Offset
        offset.copyInto(rawHeader, destinationOffset = pos)
        pos += offset.size

        // Write CRLF
        CRLF.copyInto(rawHeader, destinationOffset = pos)

        // 4. Encode to URL-Safe Base64 (No Padding)
        return Base64.UrlSafe.encode(rawHeader).trimEnd('=')
    }
}
