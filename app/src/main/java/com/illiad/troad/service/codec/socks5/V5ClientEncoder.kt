package com.illiad.troad.service.codec.socks5

import com.illiad.troad.service.codec.HeaderEncoder.encodeHeader
import com.illiad.troad.service.codec.socks5.V5AddressEncoder.encodeAddress
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest

/**
 * Encodes a client-side [Socks5CommandRequest] into a [ByteBuf].
 * only Connect requests are expected here
 */
@ChannelHandler.Sharable
object V5ClientEncoder : MessageToByteEncoder<Socks5CommandRequest?>() {
    override fun encode(ctx: ChannelHandlerContext, request: Socks5CommandRequest?, out: ByteBuf) {
        encodeHeader(out)
        out.writeByte(request!!.version().byteValue().toInt())
        out.writeByte(request.type().byteValue().toInt())
        out.writeByte(0x00)

        val dstAddrType = request.dstAddrType()
        out.writeByte(dstAddrType.byteValue().toInt())
        encodeAddress(dstAddrType, request.dstAddr(), out)
        ByteBufUtil.writeShortBE(out, request.dstPort())
        ctx.pipeline().remove(this)
    }
}

