package com.illiad.troad.service.handler.ip

import com.illiad.troad.service.HandlerNamer
import com.illiad.troad.service.handler.socks5.ConnectionHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.pcap4j.packet.IpPacket

@ChannelHandler.Sharable
object DemuxHandler : SimpleChannelInboundHandler<MutableList<IpPacket?>?>() {

    override fun channelRead0(ctx: ChannelHandlerContext?, packets: MutableList<IpPacket?>?) {
        if (ctx == null || packets == null || packets.isEmpty()) {
            return
        }
        for (packet in packets) {
            if (packet == null) {
                continue
            }
            val connection = Connection.extractConnetion(packet)
            if (connection != null) {
                var session = Demux.getSession(connection)
                if (session == null) {
                    // new session
                    session = Demux.createSession(connection)
                    session.addPacket(packet)
                    ctx.pipeline()?.addLast(HandlerNamer.name, ConnectionHandler())
                    ctx.fireChannelRead(connection)
                } else if (!session.isBufferEmpty()) {
                    // existing session with buffered packet
                    // buffer the packet
                    session.addPacket(packet)
                } else if (session.isActive()) {
                    // existing session without buffered packet, write to active channel
                    session.writeAndFlush(packet)
                }
                // existing session without buffered packet, and inactive channel, do nothing,
                //session should only be removed by the AckHandler under channel inactive event

            }
        }
    }
}