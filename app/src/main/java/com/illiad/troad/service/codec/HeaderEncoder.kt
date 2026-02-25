package com.illiad.troad.service.codec

import com.illiad.troad.service.security.SecretImp
import io.netty.buffer.ByteBuf

/**
 * Encodes a client-side illiad Header into a [ByteBuf].
 * an illiad header is a byte array of variable lenght, ended by CRLF.
 * the first 2 bytes is the length of the header. the next byte is the crypto type. then comes the signature, and a random offset.
 * if the encryption returns a fixed-length signature, the length field contains the whole length(length + cryptoType + signature + offset CRLF).
 * if the encryption returns a variable-length signature, the length field contain the length of the signature only(length + cryptoType + signature).
 */
object HeaderEncoder{

    private val CRLF = byteArrayOf(0x0D, 0x0A)

    fun encodeHeader(byteBuf: ByteBuf) {
        // get secret

        val secretBytes: ByteArray
        try {
            secretBytes = SecretImp.secret!!
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        val offset: ByteArray = SecretImp.offset()
        val signLength = SecretImp.cryptoLength
        // check if the crypto type is fixed length
        if (signLength > 0) {
            // the encryption returns a fixed-length signature, the length field contains the whole length(length + cryptoType + signature + offset CRLF).
            // 5 = 2 bytes for length + 1 byte for crypto type + 2 bytes for CRLF
            byteBuf.writeShort(signLength + offset.size + 5)
        } else {
            // the encryption returns a variable-length signature, the length field contain the length of the signature only(length + cryptoType + signature).
            // 3 = 2 bytes for length + 1 byte for crypto type
            byteBuf.writeShort(secretBytes.size + 3)
        }
        // write crypto type, signature, and offset into ByteBuffer
        byteBuf.writeByte(SecretImp.cryptoTypeByte)
        byteBuf.writeBytes(secretBytes)
        byteBuf.writeBytes(offset)
        byteBuf.writeBytes(CRLF)
    }

}