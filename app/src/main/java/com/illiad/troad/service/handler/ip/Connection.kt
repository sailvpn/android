package com.illiad.troad.service.handler.ip

import org.pcap4j.packet.IpPacket
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.IpV6Packet
import org.pcap4j.packet.Packet
import org.pcap4j.packet.TcpPacket
import org.pcap4j.packet.UdpPacket
import org.pcap4j.packet.namednumber.IpNumber
import java.net.InetAddress

/**
 * Represents a network connection with its defining characteristics.
 *
 * @property ipVersion The IP version of the connection (4 or 6).
 * @property protocol The IP protocol used (e.g., TCP, UDP).
 * @property src The source IP address.
 * @property srcPort The source port number. Can be 0 if not applicable (e.g. for non-TCP/UDP protocols).
 * @property dst The destination IP address.
 * @property dstPort The destination port number. Can be 0 if not applicable.
 */
class Connection(
    val ipVersion: Int,
    val protocol: IpNumber,
    val src: InetAddress,
    val srcPort: Int,
    val dst: InetAddress,
    val dstPort: Int
) {

    companion object {
        /**
         * Extracts connection information from a generic packet.
         *
         * This function inspects a packet to determine its IP version, protocol,
         * source and destination addresses, and ports.
         *
         * @param packet The packet to analyze.
         * @return A [Connection] object if the packet is a valid IP packet, otherwise null.
         */
        fun extractConnection(packet: Packet?): Connection? {
            val ipPacket = packet as? IpPacket ?: packet?.get(IpPacket::class.java) ?: return null
            val payload = ipPacket.payload

            val (srcPort, dstPort) = when (payload) {
                is TcpPacket -> payload.header.srcPort.valueAsInt() to payload.header.dstPort.valueAsInt()
                is UdpPacket -> payload.header.srcPort.valueAsInt() to payload.header.dstPort.valueAsInt()
                else -> return null // Only handling TCP/UDP for now
            }

            return Connection(
                ipVersion = ipPacket.header.version.value().toInt(),
                protocol = (ipPacket as? IpV4Packet)?.header?.protocol
                    ?: (ipPacket as? IpV6Packet)?.header?.nextHeader
                    ?: ipPacket.header.protocol,
                src = ipPacket.header.srcAddr,
                srcPort = srcPort,
                dst = ipPacket.header.dstAddr,
                dstPort = dstPort
            )
        }

    }

    /**
     * Custom equality check for [Connection] objects.
     * For TCP, connections are considered equal if source and destination IPs and ports match.
     * For UDP, equality is based on matching source IP and port.
     *
     * @param other The object to compare against.
     * @return `true` if the objects are considered equal, `false` otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Connection) return false

        // Standard 5-tuple check (Protocol + Src + Dst)
        if (protocol != other.protocol) return false
        if (srcPort != other.srcPort) return false
        if (dstPort != other.dstPort) return false
        if (src != other.src) return false
        if (dst != other.dst) return false

        return true
    }

    /**
     * Generates a hash code for the [Connection] object.
     * The hash code is calculated based on all properties of the connection.
     *
     * @return The hash code.
     */
    override fun hashCode(): Int {
        // Must include exactly the same fields used in equals
        var result = protocol.hashCode()
        result = 31 * result + src.hashCode()
        result = 31 * result + srcPort
        result = 31 * result + dst.hashCode()
        result = 31 * result + dstPort
        return result
    }

}