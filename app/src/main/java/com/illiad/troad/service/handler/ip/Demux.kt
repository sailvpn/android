package com.illiad.troad.service.handler.ip

import io.netty.channel.Channel
import org.pcap4j.packet.IpPacket

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

    fun getPacket(channel: Channel): Any? {
        val session = getSession(channel)
        if (session != null) {
            return session.getPacket()
        }
        return null
    }


}


