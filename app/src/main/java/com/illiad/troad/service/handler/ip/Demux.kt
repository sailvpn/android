package com.illiad.troad.service.handler.ip

import io.netty.channel.Channel
import org.pcap4j.packet.IpPacket

object Demux {
    private val pool: MutableList<Session> = mutableListOf()

    fun addSession(session: Session) {
        pool.add(session)
    }

    fun getSession(channel: Channel): Session? {

        return pool.find { it.channel?.id()?.asShortText() == channel.id().asShortText() }
    }

    fun getSession(connection: Connection): Session? {
        return pool.find { it.connection == connection }
    }

    // should only create by connection,
    // always check if session already exists before creating
    fun createSession(connection: Connection): Session {
        var session = Session(null, connection)
        pool.add(session)
        return session
    }


    fun removeSession(channel: Channel): Boolean {
        val it = pool.iterator()
        while (it.hasNext()) {
            val session = it.next()
            if (session.channel?.id()?.asShortText() == channel.id().asShortText()) {
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
                it.remove()
                return true
            }
        }
        return false
    }

    fun getPacket(channel: Channel): IpPacket? {
        val session = getSession(channel)
        if (session != null) {
            return session.getPacket()
        }
        return null
    }


}


