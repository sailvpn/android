package com.illiad.troad.service.handler.ip

import com.illiad.troad.service.HandlerNamer
import com.illiad.troad.service.handler.socks5.TcpHandler
import com.illiad.troad.service.handler.socks5.UdpHandler
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket
import org.pcap4j.packet.IpPacket
import org.pcap4j.packet.namednumber.IpNumber
import java.net.InetSocketAddress
import java.nio.ByteBuffer


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

                val protocol = connection
                // Filter out any packets that are not TCP or UDP.
                if (protocol != IpNumber.TCP && protocol != IpNumber.UDP) {
                    // You can add logging here if you want to see what's being dropped.
                    // Log.d(TAG, "Dropping non-TCP/UDP packet. Protocol: $protocol")
                    continue // Skip to the next packet
                }

                var session = Demux.getSession(connection)
                if (session == null) {
                    // new session
                    session = Demux.createSession(connection)
                }

                if (session.isActive()) {
                    //channel established
                    if (session.isBufferEmpty()) {
                        // empty buffer, send current packet
                        if (protocol == IpNumber.TCP) {
                            // TCP packet
                            session.writeAndFlush(packet.rawData)
                        } else {
                            // UDP packet
                            session.writeAndFlush(
                                DatagramPacket(
                                    Unpooled.wrappedBuffer(s5UdpHeader(connection), packet.rawData),
                                    InetSocketAddress(connection.dst, connection.dstPort),
                                    InetSocketAddress(connection.src, connection.srcPort)
                                )
                            )
                        }
                    } else {
                        // buffer not empty, append packet to buffer
                        // TCP packet
                        if (protocol == IpNumber.TCP) {
                            // TCP packet
                            session.addPacket(packet.rawData)
                        } else {
                            // UDP packet
                            session.addPacket(
                                DatagramPacket(
                                    Unpooled.wrappedBuffer(s5UdpHeader(connection), packet.rawData),
                                    InetSocketAddress(connection.dst, connection.dstPort),
                                    InetSocketAddress(connection.src, connection.srcPort)
                                )
                            )

                        }
                    }

                } else {
                    // channel not yet active, buffer the packet
                    if (protocol == IpNumber.TCP) {
                        // TCP packet
                        session.addPacket(packet.rawData)
                    } else {
                        // UDP packet
                        session.addPacket(
                            DatagramPacket(
                                Unpooled.wrappedBuffer(s5UdpHeader(connection), packet.rawData),
                                InetSocketAddress(connection.dst, connection.dstPort),
                                InetSocketAddress(connection.src, connection.srcPort)
                            )
                        )

                    }

                    if (session.channel == null) {
                        // null channel, establish channel
                        if (protocol == IpNumber.TCP) {
                            ctx.pipeline()?.addLast(HandlerNamer.name, TcpHandler())
                        } else {
                            // UDP
                            ctx.pipeline().addLast(HandlerNamer.name, UdpHandler())
                        }
                        ctx.fireChannelRead(connection)

                    }
                }

            }
            // TODO: handle non-IP packets

        }
    }

    fun s5UdpHeader(connect: Connection): ByteArray {
        val addressBytes = connect.dst.address

        // Calculate the required size for the buffer:
        // 2 (RSV) + 1 (FRAG) + 1 (ATYP) + N (address length) + 2 (port)
        val addressLength = addressBytes?.size ?: 0
        val bufferSize = 2 + 1 + 1 + addressLength + 2

        val buf = ByteBuffer.allocate(bufferSize)

        // 1. RSV (Reserved) - 2 bytes (0x0000)
        buf.putShort(0x0000)

        // 2. FRAG (Fragment) - 1 byte (0x00)
        buf.put(0x00)

        // 3. ATYP (Address Type) and DST.ADDR (Destination Address)
        if (connect.ipVersion == 4 && addressBytes != null) {
            buf.put(0x01.toByte()) // IPv4 address type
            buf.put(addressBytes) // Write the 4-byte IPv4 address
        } else if (connect.ipVersion == 6 && addressBytes != null) {
            buf.put(0x04.toByte()) // IPv6 address type
            buf.put(addressBytes) // Write the 16-byte IPv6 address
        } else {
            // This case should ideally not happen if your connection object is valid.
            // We'll write a placeholder for IPv4 (0.0.0.0) as a fallback.
            buf.put(0x01.toByte())
            buf.put(byteArrayOf(0, 0, 0, 0))
        }

        // 4. DST.PORT (Destination Port) - 2 bytes
        buf.putShort(connect.dstPort.toShort())

        // Return the underlying byte array
        return buf.array()
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