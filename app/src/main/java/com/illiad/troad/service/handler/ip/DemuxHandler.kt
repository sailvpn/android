package com.illiad.troad.service.handler.ip

import com.illiad.troad.service.HandlerNamer
import com.illiad.troad.service.handler.socks5.ConnectionHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.pcap4j.packet.IpPacket
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.IpV6ExtHopByHopOptionsPacket
import org.pcap4j.packet.IpV6Packet
import org.pcap4j.packet.namednumber.IpNumber

@ChannelHandler.Sharable
object DemuxHandler : SimpleChannelInboundHandler<MutableList<IpPacket?>?>() {

    override fun channelRead0(ctx: ChannelHandlerContext?, packets: MutableList<IpPacket?>?) {
        if (ctx == null || packets == null || packets.isEmpty()) {
            return
        }
        for (packet in packets) {
            if (packet == null) {
                continue
            } else if (packet is IpV4Packet) {
                val ipV4Packet = packet as IpV4Packet
                if (ipV4Packet.header.protocol == IpNumber.ICMPV4) {
                    // This is an ICMPv4 packet
                    continue
                    // You can then get the ICMPv4 packet if needed:
                    // val icmpV4Packet = ipV4Packet.payload as IcmpV4CommonPacket

                }
            } else if (packet is IpV6Packet) {
                val ipV6Packet = packet as IpV6Packet
                // check if the packet is an ICMPv6 packet
                if (ipV6Packet.header.nextHeader == IpNumber.ICMPV6) {
                    // This is an ICMPv6 packet
                    continue
                    // You can then get the ICMPv6 packet if needed:
                    // val icmpV6Packet = ipV6Packet.payload as IcmpV6CommonPacket
                    // ICMPV6 can also be indicvated by Hop-by-Hop option header first
                } else if (ipV6Packet.header.nextHeader == IpNumber.IPV6_HOPOPT) {
                    val hopByHopPacket = ipV6Packet.payload
                    if (hopByHopPacket is IpV6ExtHopByHopOptionsPacket) {
                        if (hopByHopPacket.header.nextHeader == IpNumber.ICMPV6) {
                            continue
                        }
                    }
                }
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

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        println("${this.javaClass.simpleName} caught exception: $cause")
        //TODO: Decide: handle it, or pass it on
        ctx.fireExceptionCaught(cause) // Pass to next handler in the pipeline
        ctx.channel().close().addListener { future ->
            {
                if (future.isSuccess) {
                    println("Channel closed successfully")
                } else {
                    println("Failed to close channel: ${future.cause()}")
                }
            }
        }
    }

}