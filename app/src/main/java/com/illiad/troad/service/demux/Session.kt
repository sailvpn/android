package com.illiad.troad.service.demux

import io.netty.channel.Channel
import java.util.TimerTask

class Session(var channel: Channel): TimerTask() {
    override fun run() {
        TODO("Not yet implemented")
    }
}