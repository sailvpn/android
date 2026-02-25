package com.illiad.troad.service.codec.ip

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.IpV6Packet
import org.pcap4j.packet.Packet
import org.pcap4j.packet.factory.PacketFactories
import org.pcap4j.packet.namednumber.DataLinkType

private const val MIN_IPV4_HEADER_SIZE = 20
private const val IPV6_HEADER_SIZE = 40 // Fixed size for IPv6 header
private const val VERSION_IPV4 = 4
private const val VERSION_IPV6 = 6

class PacketDecoder : ByteToMessageDecoder() {

    // Removed channelActive (let HeadContext handle the first read)
    // Removed channelReadComplete (let ByteToMessageDecoder handle buffering)

    /**
     * Decodes IP packets from the given ByteBuf.
     * Netty calls this repeatedly as long as the ByteBuf is readable.
     * We should only advance the readerIndex once we have a FULL packet.
     * @param byteBuf The ByteBuf containing raw IP packet data.
     */
    override fun decode(ctx: ChannelHandlerContext, byteBuf: ByteBuf, out: MutableList<Any>) {
        // While we have enough data to at least peek the version/header
        while (byteBuf.readableBytes() >= 1) {
            val readerIndexAtStart = byteBuf.readerIndex()
            val firstByte = byteBuf.getByte(readerIndexAtStart)
            val version = (firstByte.toInt() shr 4) and 0x0F

            val packetLength = when (version) {
                VERSION_IPV4 -> calculateV4Length(byteBuf)
                VERSION_IPV6 -> calculateV6Length(byteBuf)
                else -> {
                    // Critical: if we don't recognize the version, we must decide
                    // whether to skip a byte or clear the buffer to prevent a hang.
                    byteBuf.skipBytes(1)
                    -1
                }
            }

            // If length is -1 (unknown) or 0 (insufficient data to determine length),
            // return and wait for more data.
            if (packetLength <= 0) return

            // Check if the COMPLETE packet has arrived
            if (byteBuf.readableBytes() < packetLength) {
                // Not enough data. Do NOT advance readerIndex.
                // Netty will call decode again when more bytes arrive.
                return
            }

            // Successfully found a full packet. Extract and advance readerIndex.
            val packetBytes = ByteArray(packetLength)
            byteBuf.readBytes(packetBytes)

            val packet = tryParsePacket(packetBytes, version)
            if (packet != null) {
                out.add(packet)
            }
        }
    }

    private fun calculateV4Length(byteBuf: ByteBuf): Int {
        if (byteBuf.readableBytes() < 4) return 0 // Need bytes for Total Length field
        val totalLength = byteBuf.getUnsignedShort(byteBuf.readerIndex() + 2)
        return if (totalLength < MIN_IPV4_HEADER_SIZE) -1 else totalLength
    }

    private fun calculateV6Length(byteBuf: ByteBuf): Int {
        if (byteBuf.readableBytes() < IPV6_HEADER_SIZE) return 0
        val payloadLength = byteBuf.getUnsignedShort(byteBuf.readerIndex() + 4)
        return IPV6_HEADER_SIZE + payloadLength
    }

    private fun tryParsePacket(data: ByteArray, version: Int): Packet? {
        return try {
            val pcapPacket =
                PacketFactories.getFactory(Packet::class.java, DataLinkType::class.java)
                    .newInstance(data, 0, data.size, DataLinkType.RAW)

            val clazz =
                if (version == VERSION_IPV4) IpV4Packet::class.java else IpV6Packet::class.java

            if (clazz.isInstance(pcapPacket)) pcapPacket
            else pcapPacket?.get(clazz)
        } catch (e: Exception) {
            null
        }
    }

}