package com.illiad.troad.service.codec.ip

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import org.pcap4j.packet.IpPacket

/**
 * Encodes a client-side [IpPacket] into a [ByteBuf].
 *
 */
@ChannelHandler.Sharable
object PacketEncoder : MessageToByteEncoder<IpPacket>() {
    override fun encode(ctx: ChannelHandlerContext, packet: IpPacket, out: ByteBuf) {
        out.readBytes(packet.rawData)
    }
}