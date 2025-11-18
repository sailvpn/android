package com.illiad.troad.service.handler.ip

import io.netty.channel.Channel

data class Session(val connection: Connection) {
    @Volatile
    var channel: Channel? = null
    var aso: Channel? = null
    val lock = Any()

    val buffer: MutableList<Any> = mutableListOf()

    fun isActive(): Boolean {
        return channel != null && channel!!.isActive
    }

    fun writeAndFlush(packet: Any) {
        channel!!.writeAndFlush(packet)
    }

    fun close() {
        channel?.close()
        aso?.close()
    }

    fun addPacket(packet: Any) {
        synchronized(lock) {
            buffer.add(packet)
        }
    }

    fun getPacket(): Any? {
        synchronized(lock) {
            return if (buffer.isNotEmpty()) buffer.removeAt(0) else null
        }

    }

    fun isBufferEmpty(): Boolean {
        synchronized(lock) {
            return buffer.isEmpty()
        }
    }

}