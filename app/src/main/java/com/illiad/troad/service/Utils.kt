package com.illiad.troad.service

import android.os.ParcelFileDescriptor
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import java.nio.channels.FileChannel

object Utils {

    @Volatile
    var vpnInterface: ParcelFileDescriptor? = null

    @Volatile
    lateinit var vpnReadFileChannel: FileChannel

    @Volatile
    lateinit var vpnWriteFileChannel: FileChannel

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */

    fun closeOnFlush(ch: Channel) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }
}