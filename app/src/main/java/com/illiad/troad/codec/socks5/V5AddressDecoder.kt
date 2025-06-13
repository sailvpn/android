package com.illiad.troad.codec.socks5

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.util.CharsetUtil
import io.netty.util.NetUtil

/**
 * Decodes a SOCKS5 address field into its string representation.
 */
object V5AddressDecoder {

    private const val IPv6_LEN = 16

    @Throws(DecoderException::class)
    fun decodeAddress(addrType: Socks5AddressType, buf: ByteBuf): String? {
        if (addrType === Socks5AddressType.IPv4) {
            return NetUtil.intToIpAddress(ByteBufUtil.readIntBE(buf))
        }
        if (addrType === Socks5AddressType.DOMAIN) {
            val length = buf.readUnsignedByte().toInt()
            val domain = buf.toString(buf.readerIndex(), length, CharsetUtil.US_ASCII)
            buf.skipBytes(length)
            return domain
        }
        if (addrType === Socks5AddressType.IPv6) {
            if (buf.hasArray()) {
                val readerIdx = buf.readerIndex()
                buf.readerIndex(readerIdx + IPv6_LEN)
                return NetUtil.bytesToIpAddress(
                    buf.array(),
                    buf.arrayOffset() + readerIdx,
                    IPv6_LEN
                )
            } else {
                val tmp = ByteArray(IPv6_LEN)
                buf.readBytes(tmp)
                return NetUtil.bytesToIpAddress(tmp)
            }
        }
        throw DecoderException(
            "unsupported address type: " + (addrType.byteValue().toInt() and 0xFF)
        )
    }

}
