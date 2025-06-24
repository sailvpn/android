package com.illiad.troad.service.handler.ip

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.pcap4j.packet.IpPacket

@ChannelHandler.Sharable
object DmuxHandler : SimpleChannelInboundHandler<MutableList<IpPacket?>?>() {

    override fun channelRead0(ctx: ChannelHandlerContext?, packets: MutableList<IpPacket?>?) {
        if (packets == null || packets.isEmpty()) {
            return
        }
        for (packet in packets) {
            if(packet == null){
                continue
            }



        }
    }
}