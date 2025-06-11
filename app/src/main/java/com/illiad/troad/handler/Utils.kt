package com.illiad.troad.handler

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener

class Utils {
    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    companion object {
        fun closeOnFlush(ch: Channel) {
            if (ch.isActive()) {
                ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
            }
        }
    }
}
