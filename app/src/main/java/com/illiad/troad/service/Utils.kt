package com.illiad.troad.service

import android.content.res.Resources
import android.os.ParcelFileDescriptor
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

object Utils {

    var serverDomain: String = "127.0.0.1"
    var serverPort: Int = 2080
    var sharedSecret: String = "sharedSecret"

    // resources getters
    private val rss: Resources = Resources.getSystem()

    fun getString(id: Int): String {
        return rss.getString(id)
    }

    fun getInt(id: Int): Int {
        return rss.getInteger(id)
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */

    fun closeOnFlush(ch: Channel) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }
}