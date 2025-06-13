package com.illiad.troad.service.codec.socks5

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.EncoderException
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.util.CharsetUtil
import io.netty.util.NetUtil

/**
 * Encodes a SOCKS5 address into binary representation.
 */
object V5AddressEncoder {
    fun encodeAddress(addrType: Socks5AddressType, addrValue: String?, out: ByteBuf) {
        val typeVal = addrType.byteValue()
        if (typeVal == Socks5AddressType.IPv4.byteValue()) {
            if (addrValue != null) {
                out.writeBytes(NetUtil.createByteArrayFromIpAddressString(addrValue))
            } else {
                out.writeInt(0)
            }
        } else if (typeVal == Socks5AddressType.DOMAIN.byteValue()) {
            if (addrValue != null) {
                out.writeByte(addrValue.length)
                out.writeCharSequence(addrValue, CharsetUtil.US_ASCII)
            } else {
                out.writeByte(0)
            }
        } else if (typeVal == Socks5AddressType.IPv6.byteValue()) {
            if (addrValue != null) {
                out.writeBytes(NetUtil.createByteArrayFromIpAddressString(addrValue))
            } else {
                out.writeLong(0)
                out.writeLong(0)
            }
        } else {
            throw EncoderException(
                "unsupported addrType: " + (addrType.byteValue().toInt() and 0xFF)
            )
        }
    }
}


