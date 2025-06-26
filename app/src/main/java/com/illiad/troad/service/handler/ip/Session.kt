package com.illiad.troad.service.handler.ip

import io.netty.channel.Channel
import org.pcap4j.packet.IpPacket

data class Session(
    @Volatile
    var channel: Channel? = null,
    @Volatile
    var connection: Connection? = null
) {

    val buffer: MutableList<IpPacket> = mutableListOf()

    fun setSessionChannel(channel: Channel?) {
        this.channel = channel
    }

    fun setSessionConnection(connection: Connection?) {
        this.connection = connection
    }

    fun isActive(): Boolean {
        return channel?.isActive ?: false
    }

    fun writeAndFlush(packet: IpPacket) {
        channel?.writeAndFlush(packet)
    }

    fun close() {
        channel?.close()
    }

    fun addPacket(packet: IpPacket) {
        buffer.add(packet)
    }

    fun getPacket(): IpPacket? {
        return if (buffer.size > 0) buffer.removeAt(0) else null

    }

    fun isBufferEmpty(): Boolean {
        return buffer.isEmpty()
    }

}