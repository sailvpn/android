package com.illiad.troad.service.handler.socks5.udp

import com.illiad.troad.Utils.fildesChannel
import com.illiad.troad.service.handler.ip.Demux
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket
import org.pcap4j.packet.IpPacket
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.IpV6Packet
import org.pcap4j.packet.UdpPacket
import org.pcap4j.packet.UnknownPacket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import org.pcap4j.packet.namednumber.IpNumber
import org.pcap4j.packet.namednumber.IpVersion
import org.pcap4j.packet.namednumber.UdpPort

class ResHandler : SimpleChannelInboundHandler<DatagramPacket?>() {

    @Throws(Exception::class)
    override fun channelRead0(ctx: ChannelHandlerContext, res: DatagramPacket?) {
        if (res != null) {
            val session = Demux.getSession(ctx.channel())

            if (session != null) {

                val content = res.content()

                // 1. Parse the SOCKS5 UDP header from the received datagram.
                val socks5Header = parseSocks5UdpHeader(content) ?: return
                if (socks5Header.frag != 0.toByte()) {
                    // UDP fragmentation is not supported in this simple case.
                    return
                }

                // 2. Extract the actual UDP payload data.
                // The remaining bytes in the buffer after parsing the SOCKS5 header are the UDP payload.
                val payloadData = ByteArray(content.readableBytes())
                content.readBytes(payloadData)

                // 3. Use the original connection info from the session to construct the response packet.
                //    The source of the response is the SOCKS5 destination.
                //    The destination of the response is the original client's source.
                val srcAddr = socks5Header.dstAddr
                val srcPort = UdpPort(socks5Header.dstPort.toShort(), "")
                val dstAddr = session.connection.src
                val dstPort = UdpPort(session.connection.srcPort.toShort(), "")

                // 4. Build the new UDP and IP packets using pcap4j builders.
                val udpPacketBuilder = UdpPacket.Builder()
                    .srcPort(srcPort)
                    .dstPort(dstPort)
                    .srcAddr(srcAddr)
                    .dstAddr(dstAddr)
                    .payloadBuilder(UnknownPacket.Builder().rawData(payloadData))
                    .correctChecksumAtBuild(true)
                    .correctLengthAtBuild(true)

                val ipPacket: IpPacket = if (srcAddr is Inet4Address && dstAddr is Inet4Address) {
                    // Build an IPv4 Packet
                    IpV4Packet.Builder()
                        .version(IpVersion.IPV4)
                        .protocol(IpNumber.UDP)
                        .srcAddr(srcAddr)
                        .dstAddr(dstAddr)
                        .payloadBuilder(udpPacketBuilder)
                        .correctChecksumAtBuild(true)
                        .correctLengthAtBuild(true)
                        .build()
                } else if (srcAddr is Inet6Address && dstAddr is Inet6Address) {
                    // Build an IPv6 Packet
                    IpV6Packet.Builder()
                        .version(IpVersion.IPV6)
                        .nextHeader(IpNumber.UDP)
                        .srcAddr(srcAddr)
                        .dstAddr(dstAddr)
                        .payloadBuilder(udpPacketBuilder)
                        .correctLengthAtBuild(true)
                        .build()
                } else {
                    // IP version mismatch, cannot proceed
                    return
                }


                // 5. Write the raw bytes of the newly created packet to the TUN interface.
                //    The PacketWriterHandler is responsible for writing raw byte arrays to the TUN.
                fildesChannel?.writeAndFlush(ipPacket.rawData)

                // forward next UDP packet
                if (!session.isBufferEmpty()) {
                    ctx.channel().writeAndFlush(session.getPacket())

                }

            }
        }
    }

    /**
     * Represents the parsed SOCKS5 UDP request header.
     */
    private data class Socks5UdpHeader(
        val rsv: Short,
        val frag: Byte,
        val atyp: Byte,
        val dstAddr: InetAddress,
        val dstPort: Int
    )

    /**
     * Parses the SOCKS5 UDP header from a ByteBuf.
     * @return A Socks5UdpHeader or null if parsing fails.
     */
    private fun parseSocks5UdpHeader(buf: ByteBuf): Socks5UdpHeader? {
        // A SOCKS5 UDP header is at least 10 bytes long (for IPv4).
        if (buf.readableBytes() < 10) return null

        val rsv = buf.readShort()
        val frag = buf.readByte()
        val atyp = buf.readByte()

        val dstAddr: InetAddress? = when (atyp) {
            Socks5AddressType.IPv4.byteValue() -> {
                if (buf.readableBytes() < 4) return null
                val addrBytes = ByteArray(4)
                buf.readBytes(addrBytes)
                Inet4Address.getByAddress(addrBytes)
            }

            Socks5AddressType.IPv6.byteValue() -> {
                if (buf.readableBytes() < 16) return null
                val addrBytes = ByteArray(16)
                buf.readBytes(addrBytes)
                Inet6Address.getByAddress(addrBytes)
            }
            // DOMAINNAME is not handled here as it's not expected in a response from the proxy
            else -> return null
        }

        if (dstAddr == null || buf.readableBytes() < 2) return null

        val dstPort = buf.readUnsignedShort()

        return Socks5UdpHeader(rsv, frag, atyp, dstAddr, dstPort)
    }

    /**
     * SOCKS5 Address Type constants as per RFC 1928.
     */
    private enum class Socks5AddressType(private val value: Int) {
        IPv4(0x01),
        DOMAINNAME(0x03),
        IPv6(0x04);

        fun byteValue(): Byte = value.toByte()
    }

}

