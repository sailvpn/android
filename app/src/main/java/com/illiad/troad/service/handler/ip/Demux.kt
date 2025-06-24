package com.illiad.troad.service.handler.ip

import io.netty.channel.Channel
import io.netty.channel.ChannelId
object Demux {
    private val pool: MutableList<Session> = mutableListOf()

    fun addSession(session: Session) {
        pool.add(session)
    }

    fun getByChannel(channel: Channel): Session? {

        return pool.find { it.channel?.id()?.asShortText() == channel.id().asShortText() }
    }

    fun getByConnection(connection: Connection): Session? {
        return pool.find { it.connection == connection }
    }

}


