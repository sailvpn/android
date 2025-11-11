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

class Connection(
    val ipVersion: Int, // 4 or 6
    val protocol: IpNumber, // e.g., "TCP", "UDP", "ICMP", or IP protocol number as string
    val src: InetAddress,
    val srcPort: Int, // Nullable if not TCP or UDP
    val dst: InetAddress,
    val dstPort: Int // Nullable if not TCP or UDP
) {

    // equal() and hashCode() are automatically generated for data classes

    companion object {
        fun extractConnetion(packet: Packet?): Connection? { // Renamed from extractConnectionInfo to match file
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
                    // Note: ipPacket.header.version is an IpVersion object.
                    // IpVersion.INET4 has value() == 4, IpVersion.INET6 has value() == 6
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
                // protocolName will be "TCP" from ipPacket.header.protocol.name()
            }
            // Check for UDP Packet
            else if (ipPacket.payload is UdpPacket) {
                val udpPacket = ipPacket.payload as UdpPacket
                sourcePort = udpPacket.header.srcPort.valueAsInt()
                destinationPort = udpPacket.header.dstPort.valueAsInt()
                // protocolName will be "UDP"
            }
            // You can add more else if blocks for other protocols if needed,
            // though they might not have "ports" in the same sense (e.g., ICMP has type/code).
            else {
                // For protocols like ICMP, sourcePort and destinationPort will remain null.
                // protocolName is already set from the IP header.
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

// fun main() {
    // This main function is from your original file, kept for context.
    // Conceptual examples of how you might test this would go here.
    // For instance, you'd need to create or mock Pcap4j Packet objects.

    // Example (conceptual - requires Pcap4j setup to build actual packets):
    /*
    val mockIpV4Bytes = byteArrayOf(
        0x45, 0x00, 0x00, 0x1c, // Version, IHL, TOS, Total Length (28 bytes)
        0x12, 0x34, 0x00, 0x00, // Identification, Flags, Fragment Offset
        0x40, 0x06, 0x00, 0x00, // TTL (64), Protocol (6=TCP), Header Checksum (dummy)
        0x01, 0x02, 0x03, 0x04, // Source IP (1.2.3.4)
        0x05, 0x06, 0x07, 0x08, // Destination IP (5.6.7.8)
        // TCP Header (8 bytes for this dummy example)
        0x00, 0x50, 0x00, 0x51, // Source Port (80), Dest Port (81)
        0x00, 0x00, 0x00, 0x00  // Sequence Number (dummy)
        // ... more TCP fields if it were a real packet
    )
    val pcapIpV4TcpPacket = org.pcap4j.packet.IpV4Packet.newPacket(mockIpV4Bytes, 0, mockIpV4Bytes.size)

    val info = extractAdrss(pcapIpV4TcpPacket)
    if (info != null) {
        println("Version: ${info.ipVersion}")
        println("Source Address: ${info.sourceAddress.hostAddress}")
        println("Destination Address: ${info.destinationAddress.hostAddress}")
        if (info.sourcePort != null) {
            println("Source Port: ${info.sourcePort}")
        }
        if (info.destinationPort != null) {
            println("Destination Port: ${info.destinationPort}")
        }
        println("Protocol: ${info.protocol}")
    }
    */
// }

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