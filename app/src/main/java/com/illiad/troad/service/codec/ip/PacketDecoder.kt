package com.illiad.troad.service.codec.ip

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.IpV6Packet
import org.pcap4j.packet.Packet

class PacketDecoder : ByteToMessageDecoder() {

    companion object {
        private const val MAX_IP_PACKET_SIZE = 65535
        private const val MIN_IPV4_SIZE = 20
        private const val IPV6_FIXED_HEADER_SIZE = 40
        private const val VERSION_IPV4 = 4
        private const val VERSION_IPV6 = 6
    }

    override fun decode(ctx: ChannelHandlerContext, byteBuf: ByteBuf, out: MutableList<Any>) {
        while (byteBuf.readableBytes() >= 1) {
            val startIdx = byteBuf.readerIndex()
            val version = (byteBuf.getByte(startIdx).toInt() shr 4) and 0x0F

            val packetLength = when (version) {
                VERSION_IPV4 -> calculateV4Length(byteBuf, startIdx)
                VERSION_IPV6 -> calculateV6Length(byteBuf, startIdx)
                else -> {
                    // Skip invalid byte and keep looking in the same loop execution
                    byteBuf.skipBytes(1)
                    -2 // Special flag to continue loop
                }
            }

            // If we need more data to determine length, exit and wait for Netty
            if (packetLength == 0) return

            // If invalid version (flag -2) or bad length (flag -1), continue while loop
            if (packetLength < 0) continue

            // Safety check: Don't wait for impossible packet sizes
            if (packetLength > MAX_IP_PACKET_SIZE) {
                byteBuf.skipBytes(1)
                continue
            }

            // Check if the COMPLETE packet has arrived
            if (byteBuf.readableBytes() < packetLength) return

            // Extract data.
            // Note: If you must use ByteArray, this is the point of allocation.
            val packetBytes = ByteArray(packetLength)
            byteBuf.readBytes(packetBytes)

            tryParsePacket(packetBytes, version)?.let { out.add(it) }
        }
        
    }

    private fun calculateV4Length(byteBuf: ByteBuf, start: Int): Int {
        if (byteBuf.readableBytes() < 4) return 0
        val totalLength = byteBuf.getUnsignedShort(start + 2)
        return if (totalLength < MIN_IPV4_SIZE) -1 else totalLength
    }

    private fun calculateV6Length(byteBuf: ByteBuf, start: Int): Int {
        if (byteBuf.readableBytes() < IPV6_FIXED_HEADER_SIZE) return 0
        // IPv6 Payload Length field (at offset 4) excludes the 40-byte fixed header
        val payloadLength = byteBuf.getUnsignedShort(start + 4)
        return IPV6_FIXED_HEADER_SIZE + payloadLength
    }

    private fun tryParsePacket(data: ByteArray, version: Int): Packet? {
        return try {
            // Direct static factory methods are much more reliable than the generic PacketFactories
            if (version == VERSION_IPV4) {
                IpV4Packet.newPacket(data, 0, data.size)
            } else if (version == VERSION_IPV6) {
                IpV6Packet.newPacket(data, 0, data.size)
            } else {
                null
            }
        } catch (e: Exception) {
            // Log the exception here to see if parsing is failing due to malformed headers
            println("Pcap4j parsing error: ${e.message}")
            null
        }
    }

}