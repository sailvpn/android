package com.illiad.troad.service.handler.ip

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.util.ReferenceCountUtil
import io.netty.util.ReferenceCounted

object Demux {
    private val pool: MutableList<Session> = mutableListOf()

    fun getSession(channel: Channel): Session? {

        return pool.find { it.channel?.id() == channel.id() }
    }

    fun getSession(connection: Connection): Session? {
        return pool.find { it.connection == connection }
    }

    // should only create by connection,
    // always check if session already exists before creating
    fun createSession(connection: Connection): Session {
        val session = Session(connection)
        pool.add(session)
        return session
    }


    fun removeSession(channel: Channel): Boolean {
        val it = pool.iterator()
        while (it.hasNext()) {
            val session = it.next()
            if (session.channel?.id() == channel.id()) {
                if (session.aso?.isActive == true) {
                    session.aso?.writeAndFlush(Unpooled.EMPTY_BUFFER)?.addListener(ChannelFutureListener.CLOSE)
                }
                if (session.channel?.isActive == true) {
                    session.channel?.writeAndFlush(Unpooled.EMPTY_BUFFER)?.addListener(ChannelFutureListener.CLOSE)
                }
                for (packet in session.buffer) {
                    if (packet is ReferenceCounted) {
                        ReferenceCountUtil.release(packet)
                    }
                }
                session.buffer.clear()
                it.remove()
                return true
            }
        }
        return false
    }

    fun removeSession(connection: Connection): Boolean {
        val it = pool.iterator()
        while (it.hasNext()) {
            val session = it.next()
            if (session.connection == connection) {
                if (session.aso?.isActive == true) {
                    session.aso?.writeAndFlush(Unpooled.EMPTY_BUFFER)?.addListener(ChannelFutureListener.CLOSE)
                }
                if (session.channel?.isActive == true) {
                    session.channel?.writeAndFlush(Unpooled.EMPTY_BUFFER)?.addListener(ChannelFutureListener.CLOSE)
                }
                for (packet in session.buffer) {
                    if (packet is ReferenceCounted) {
                        ReferenceCountUtil.release(packet)
                    }
                }
                session.buffer.clear()
                it.remove()
                return true
            }
        }
        return false
    }

}


