package com.illiad.troad.service

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import java.io.FileInputStream
import java.io.FileOutputStream

object Utils {

    lateinit var vpnReadStream: FileInputStream
    lateinit var vpnWriteStream: FileOutputStream
    var vpnThread: Thread? = null // Thread for handling VPN packet I/O
    var isRunning = false

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */

    fun closeOnFlush(ch: Channel) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }
}