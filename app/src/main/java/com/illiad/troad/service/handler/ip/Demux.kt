package com.illiad.troad.service.handler.ip

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelId
import io.netty.util.ReferenceCountUtil
import java.util.concurrent.ConcurrentHashMap

object Demux {
    // Key by Connection (which should implement equals/hashCode correctly)
    private val sessionMap = ConcurrentHashMap<Connection, Session>()

    // Reverse lookup for channel closing events
    private val channelToConnection = ConcurrentHashMap<ChannelId, Connection>()

    fun getSession(channel: Channel): Session? {
        val conn = channelToConnection[channel.id()] ?: return null
        return sessionMap[conn]
    }

    fun getSession(connection: Connection): Session? = sessionMap[connection]

    fun createSession(connection: Connection): Session {
        val session = Session(connection)
        sessionMap[connection] = session
        // Note: Map channel ID once the outbound channel is actually assigned
        return session
    }

    // Call this when the outbound channel becomes active
    fun mapChannel(channel: Channel, connection: Connection) {
        channelToConnection[channel.id()] = connection
    }

    fun removeSession(connection: Connection): Boolean {
        val session = sessionMap.remove(connection) ?: return false

        // Clean up reverse lookup
        session.channel?.id()?.let { channelToConnection.remove(it) }

        // Graceful Close
        closeCompontent(session.aso)
        closeCompontent(session.channel)

        // Clear and Release Buffer
        session.buffer.forEach { packet ->
            ReferenceCountUtil.release(packet)
        }
        session.buffer.clear()

        return true
    }

    private fun closeCompontent(ch: Channel?) {
        if (ch?.isActive == true) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }
}



