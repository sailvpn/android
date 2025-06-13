package com.illiad.troad.codec.socks5

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.EncoderException
import io.netty.handler.codec.MessageToByteEncoder
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse
import io.netty.handler.codec.socksx.v5.Socks5Message
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponse
import io.netty.util.internal.StringUtil

/**
 * Encodes a server-side [Socks5Message] into a [ByteBuf].
 */
@ChannelHandler.Sharable
class V5ServerEncoder : MessageToByteEncoder<Socks5Message?>() {
    override fun encode(ctx: ChannelHandlerContext?, msg: Socks5Message?, out: ByteBuf) {
        if (msg is Socks5InitialResponse) {
            encodeAuthMethodResponse(msg, out)
        } else if (msg is Socks5PasswordAuthResponse) {
            encodePasswordAuthResponse(msg, out)
        } else if (msg is Socks5CommandResponse) {
            encodeCommandResponse(msg, out)
        } else {
            throw EncoderException("unsupported message type: " + StringUtil.simpleClassName(msg))
        }
    }

    private fun encodeCommandResponse(msg: Socks5CommandResponse, out: ByteBuf) {
        out.writeByte(msg.version().byteValue().toInt())
        out.writeByte(msg.status().byteValue().toInt())
        out.writeByte(0x00)

        val bndAddrType = msg.bndAddrType()
        out.writeByte(bndAddrType.byteValue().toInt())
        V5AddressEncoder.encodeAddress(bndAddrType, msg.bndAddr(), out)

        ByteBufUtil.writeShortBE(out, msg.bndPort())
    }

    companion object {
        private fun encodeAuthMethodResponse(msg: Socks5InitialResponse, out: ByteBuf) {
            out.writeByte(msg.version().byteValue().toInt())
            out.writeByte(msg.authMethod().byteValue().toInt())
        }

        private fun encodePasswordAuthResponse(msg: Socks5PasswordAuthResponse, out: ByteBuf) {
            out.writeByte(0x01)
            out.writeByte(msg.status().byteValue().toInt())
        }
    }
}
