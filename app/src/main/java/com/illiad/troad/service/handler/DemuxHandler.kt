package com.illiad.troad.service.handler

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

@Sharable
object DemuxHandler : ByteToMessageDecoder() {

    override fun decode(ctx: ChannelHandlerContext?, buf: ByteBuf?, out: MutableList<Any?>?) {
        TODO("Not yet implemented")
    }

}