package com.illiad.troad.service.handler.ip

import org.pcap4j.packet.IpPacket
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.IpV6Packet
import org.pcap4j.packet.TcpPacket
import org.pcap4j.packet.UdpPacket
import org.pcap4j.packet.Packet // For the most generic packet object
import java.net.InetAddress

data class Connection(
    val sourceAddress: String,
    val destinationAddress: String,
    val sourcePort: Int?, // Nullable if not TCP or UDP
    val destinationPort: Int?, // Nullable if not TCP or UDP
    val protocol: String, // e.g., "TCP", "UDP", "ICMP", or IP protocol number as string
    val ipVersion: Int // 4 or 6
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
                println("Packet does not contain an IP layer.")
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
                println("Could not reliably determine IP version for packet.")
                return null
            }

            val sourceAddress: InetAddress = ipPacket.header.srcAddr
            val destinationAddress: InetAddress = ipPacket.header.dstAddr
            var sourcePort: Int? = null
            var destinationPort: Int? = null
            // Get protocol name (e.g., TCP, UDP) or number if name is not standard
            var protocolName: String =
                ipPacket.header.protocol.name() ?: ipPacket.header.protocol.value().toString()


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
                println("IP packet payload is not TCP or UDP. Protocol: ${ipPacket.header.protocol} (Name: $protocolName)")
            }

            return Connection(
                sourceAddress = sourceAddress.hostAddress,
                destinationAddress = destinationAddress.hostAddress,
                sourcePort = sourcePort,
                destinationPort = destinationPort,
                protocol = protocolName,
                ipVersion = determinedIpVersion
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

}