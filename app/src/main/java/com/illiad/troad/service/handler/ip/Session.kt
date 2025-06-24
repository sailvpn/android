package com.illiad.troad.service.handler.ip

import io.netty.channel.Channel
import org.pcap4j.packet.IpPacket

data class Session(
    var channel: Channel? = null,
    var connection: Connection? = null
) {

    val buffer: MutableList<IpPacket> = mutableListOf()

    fun isActive(): Boolean {
        return channel?.isActive ?: false
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

}