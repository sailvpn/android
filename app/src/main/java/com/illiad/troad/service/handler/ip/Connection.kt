package com.illiad.troad.service.handler.ip

import org.pcap4j.packet.IpPacket
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.IpV6Packet
import org.pcap4j.packet.TcpPacket
import org.pcap4j.packet.UdpPacket
import org.pcap4j.packet.Packet // For the most generic packet object
import org.pcap4j.packet.namednumber.IpNumber
import org.pcap4j.packet.namednumber.IpNumber.TCP
import org.pcap4j.packet.namednumber.IpNumber.UDP

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
        fun extractConnetion(packet: Packet?): Connection? {
            if (packet == null) {
                return null
            }

            val ipPacket: IpPacket? = when {
                packet is IpPacket -> packet
                packet.contains(IpPacket::class.java) -> packet.get(IpPacket::class.java)
                else -> null
            }

            if (ipPacket == null) {
                return null
            }

            // Determine IP Version
            val determinedIpVersion: Int = when (ipPacket) {
                is IpV4Packet -> 4
                is IpV6Packet -> 6
                else -> {
                    // Fallback by checking the version field in the generic IpPacket header
                    val versionFromHeader = ipPacket.header.version.value().toInt()
                    if (versionFromHeader == 4 || versionFromHeader == 6) {
                        versionFromHeader
                    } else {
                        println("Unknown IP version in packet header: ${ipPacket.header.version}")
                        0 // Or handle as an error, perhaps return null or throw exception
                    }
                }
            }
            // If determinedIpVersion is 0 here, you might want to return null or throw
            if (determinedIpVersion == 0) {
                return null
            }

            val protocol = when (packet) {
                is IpV4Packet -> packet.header.protocol
                is IpV6Packet -> packet.header.nextHeader
                else -> null
            }


            var sourcePort: Int? = null
            var destinationPort: Int? = null

            // Check for TCP Packet
            if (ipPacket.payload is TcpPacket) {
                val tcpPacket = ipPacket.payload as TcpPacket
                sourcePort = tcpPacket.header.srcPort.valueAsInt()
                destinationPort = tcpPacket.header.dstPort.valueAsInt()
            }
            // Check for UDP Packet
            else if (ipPacket.payload is UdpPacket) {
                val udpPacket = ipPacket.payload as UdpPacket
                sourcePort = udpPacket.header.srcPort.valueAsInt()
                destinationPort = udpPacket.header.dstPort.valueAsInt()
            }
            else {
                println("IP packet payload is not TCP or UDP. Protocol: ${ipPacket.header.protocol} (Name: $ipPacket.header.protocol)")
            }

            return Connection(
                ipVersion = determinedIpVersion,
                protocol = protocol ?: ipPacket.header.protocol,
                src = ipPacket.header.srcAddr,
                srcPort = sourcePort ?: 0,
                dst = ipPacket.header.dstAddr,
                dstPort = destinationPort ?: 0
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
        var eql = false
        if (other is Connection) {
            if (protocol == TCP && src == other.src &&
                dst == other.dst &&
                srcPort == other.srcPort &&
                dstPort == other.dstPort
            ) {
                eql = true
            } else if (protocol == UDP && src == other.src &&
                srcPort == other.srcPort
            ) {
                eql = true
            }
        }
        return eql
    }

    /**
     * Generates a hash code for the [Connection] object.
     * The hash code is calculated based on all properties of the connection.
     *
     * @return The hash code.
     */
    override fun hashCode(): Int {
        var result = ipVersion
        result = 31 * result + srcPort
        result = 31 * result + dstPort
        result = 31 * result + protocol.hashCode()
        result = 31 * result + src.hashCode()
        result = 31 * result + dst.hashCode()
        return result
    }

}