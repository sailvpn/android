package com.illiad.troad.service.codec.ip

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.IpV6Packet
import org.pcap4j.packet.Packet
import org.pcap4j.packet.UnknownPacket
import org.pcap4j.packet.factory.PacketFactories
import org.pcap4j.packet.namednumber.DataLinkType

private const val MIN_IPV4_HEADER_SIZE = 20
private const val IPV6_HEADER_SIZE = 40 // Fixed size for IPv6 header
private const val VERSION_IPV4 = 4
private const val VERSION_IPV6 = 6

class PacketDecoder : ByteToMessageDecoder() {

    /**
     * Decodes IP packets from the given ByteBuf.
     *
     * @param byteBuf The ByteBuf containing raw IP packet data.
     *                The readerIndex of this buffer will be advanced as packets are consumed.
     * add the parsed IpPacket objects to the out list.
     */
    override fun decode(ctx: ChannelHandlerContext?, byteBuf: ByteBuf?, out: MutableList<Any?>?) {

        if (byteBuf == null || !byteBuf.isReadable || byteBuf.readableBytes() < 1) {
            return
        }

        while (byteBuf.readableBytes() > 0) {
            // Peek at the first byte to get the IP version without advancing readerIndex yet
            val firstByte = byteBuf.getByte(byteBuf.readerIndex())
            val version = (firstByte.toInt() shr 4) and 0x0F

            when (version) {
                VERSION_IPV4 -> {
                    val packet = parseIpV4Packet(byteBuf)
                    if (packet != null) {
                        out!!.add(packet)
                    } else {
                        // Parsing failed or not enough data for a full IPv4 packet, stop further processing
                        // as we can't reliably determine the next packet's start.
                        println("Failed to parse IPv4 packet or insufficient data, stopping.")
                    }
                }

                VERSION_IPV6 -> {
                    val packet = parseIpV6Packet(byteBuf)
                    if (packet != null) {
                        out!!.add(packet)
                    } else {
                        println("Failed to parse IPv6 packet or insufficient data, stopping.")
                    }
                }

                else -> {
                    println("Unknown IP version: $version. Consuming 1 byte and trying again or stopping.")
                    // This could be non-IP data or a malformed packet.
                    // Depending on requirements, you might skip a byte or stop.
                    // For now, let's log and break to avoid consuming potentially valid data incorrectly.
                    // byteBuf.skipBytes(1) // Example: if you want to try to skip malformed data
                    break
                }
            }
        }
        byteBuf.release()
        ctx!!.fireChannelRead(out)
    }

    private fun parseIpV4Packet(byteBuf: ByteBuf): IpV4Packet? {
        if (byteBuf.readableBytes() < MIN_IPV4_HEADER_SIZE) {
            println("Not enough data for minimal IPv4 header. Readable: ${byteBuf.readableBytes()}")
            return null
        }

        // Peek IHL (Internet Header Length) - lower 4 bits of the first byte
        val ihl = byteBuf.getByte(byteBuf.readerIndex()).toInt() and 0x0F
        val headerLength = ihl * 4
        if (headerLength < MIN_IPV4_HEADER_SIZE) {
            println("Invalid IPv4 IHL: $ihl. Calculated header length: $headerLength bytes.")
            // This is likely a malformed packet. We cannot proceed reliably.
            return null // Or throw an exception
        }

        // Peek Total Length (bytes 2 and 3 of the IP header)
        // Ensure we have enough bytes to read the total length field itself
        if (byteBuf.readableBytes() < 4) { // Need at least first 4 bytes for version, IHL, total length
            println("Not enough data to read IPv4 total length field.")
            return null
        }
        val totalLength =
            byteBuf.getUnsignedShort(byteBuf.readerIndex() + 2) // Big-endian by default

        if (totalLength < headerLength) {
            println("Invalid IPv4 total length: $totalLength. Header length: $headerLength.")
            return null // Or throw
        }

        if (byteBuf.readableBytes() < totalLength) {
            println("Not enough data for declared IPv4 total length. Readable: ${byteBuf.readableBytes()}, Needed: $totalLength")
            return null
        }

        // Now read the exact number of bytes for this packet
        val packetBytes = ByteArray(totalLength)
        byteBuf.readBytes(packetBytes) // Consume bytes from buffer

        try {
            val pcapPacket: Packet? =
                PacketFactories.getFactory(Packet::class.java, DataLinkType::class.java)
                    .newInstance(
                        packetBytes, 0, packetBytes.size, DataLinkType.RAW
                    ) // Or DataLinkType.IPV4

            if (pcapPacket is IpV4Packet) {
                return pcapPacket
            } else if (pcapPacket != null && pcapPacket.contains(IpV4Packet::class.java)) {
                return pcapPacket.get(IpV4Packet::class.java)
            } else if (pcapPacket is UnknownPacket) {
                println(
                    "Pcap4j parsed IPv4 data as UnknownPacket. Hex: ${
                        ByteBufUtil.hexDump(
                            packetBytes
                        )
                    }"
                )
            } else {
                println("Pcap4j failed to parse IPv4 packet or parsed as wrong type: ${pcapPacket?.javaClass?.simpleName}")
            }
        } catch (e: Exception) {
            println("Error parsing IPv4 packet with Pcap4j: ${e.message}")
            // Consider logging the packetBytes in hex for debugging
            // e.printStackTrace()
        }
        return null
    }

    private fun parseIpV6Packet(byteBuf: ByteBuf): IpV6Packet? {
        // Minimum length for an IPv6 header is fixed.
        if (byteBuf.readableBytes() < IPV6_HEADER_SIZE) {
            println("Not enough data for fixed IPv6 header. Readable: ${byteBuf.readableBytes()}, Needed: $IPV6_HEADER_SIZE")
            return null
        }

        // In IPv6, the header has a fixed size of 40 bytes.
        // The "Payload Length" field (at offset 4, 2 bytes) in the IPv6 header
        // indicates the size of the payload *after* the 40-byte IPv6 header.
        // It does NOT include the size of the IPv6 header itself.
        // The total length of the IPv6 packet is IPV6_HEADER_SIZE (40) + Payload Length.

        // Peek Payload Length (bytes 4 and 5 of the IPv6 header)
        // Ensure we have enough bytes to read the payload length field itself (within the first 40 bytes)
        val payloadLength =
            byteBuf.getUnsignedShort(byteBuf.readerIndex() + 4) // Read from offset 4 (0-indexed)

        // Calculate the total length of this IPv6 packet
        val totalPacketLength = IPV6_HEADER_SIZE + payloadLength

        if (totalPacketLength > 65535 + IPV6_HEADER_SIZE) { // Max payload length is 65535
            println("Calculated IPv6 total packet length is excessive: $totalPacketLength. Payload length: $payloadLength")
            // This could indicate a malformed packet.
            return null
        }

        if (byteBuf.readableBytes() < totalPacketLength) {
            println("Not enough data for declared IPv6 total packet length. Readable: ${byteBuf.readableBytes()}, Needed: $totalPacketLength")
            return null
        }

        // Now read the exact number of bytes for this IPv6 packet
        val packetBytes = ByteArray(totalPacketLength)
        byteBuf.readBytes(packetBytes) // Consume bytes from the buffer

        try {
            // Pcap4j expects raw packet data. DataLinkType.RAW is suitable when you have
            // the IP packet itself (without lower-level headers like Ethernet).
            // You could also use DataLinkType.IPV6.
            val pcapPacket: Packet? =
                PacketFactories.getFactory(Packet::class.java, DataLinkType::class.java)
                    .newInstance(packetBytes, 0, packetBytes.size, DataLinkType.RAW)

            if (pcapPacket is IpV6Packet) {
                return pcapPacket
            } else if (pcapPacket != null && pcapPacket.contains(IpV6Packet::class.java)) {
                // This case handles scenarios where the packet might be wrapped,
                // e.g. if DataLinkType.NULL was used and it had a Loopback header.
                // For DataLinkType.RAW or .IPV6, this is less common but good for robustness.
                return pcapPacket.get(IpV6Packet::class.java)
            } else if (pcapPacket is UnknownPacket) {
                println(
                    "Pcap4j parsed IPv6 data as UnknownPacket. Hex: ${
                        ByteBufUtil.hexDump(
                            packetBytes
                        )
                    }"
                )
            } else {
                println("Pcap4j failed to parse IPv6 packet or parsed as wrong type: ${pcapPacket?.javaClass?.simpleName}")
            }
        } catch (e: Exception) {
            println("Error parsing IPv6 packet with Pcap4j: ${e.message}")
            // Consider logging the packetBytes in hex for debugging, e.g., ByteBufUtil.hexDump(packetBytes)
            // e.printStackTrace() // For more detailed stack trace during development
        }
        return null
    }
}