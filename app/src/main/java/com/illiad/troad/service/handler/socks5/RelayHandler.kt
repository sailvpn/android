package com.illiad.troad.service.handler.socks5

import com.illiad.troad.service.Utils
import com.illiad.troad.service.handler.ip.Demux
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler

/**
 * This handler relay the incoming data to the TUN device (e.g., IP packets), without any touch
 *
 */
class RelayHandler() : SimpleChannelInboundHandler<ByteBuf>() {

    override fun channelRead0(ctx: ChannelHandlerContext?, byteBuf: ByteBuf?) {

        if (ctx == null || byteBuf == null) {
            return
        }

        if (!Utils.vpnWriteStream.channel.isOpen) {
            // VpnService is not running
            Demux.removeSession(ctx.channel())
            ctx.close() // Close the client connection as we can't process its data
            return
        }

        val readableBytes = byteBuf.readableBytes()
        if (readableBytes == 0) {
            return
        }

        // relay bytes from bytebuffer to Tun-based output stream
        byteBuf.readBytes(Utils.vpnWriteStream, readableBytes)

        val session = Demux.getSession(ctx.channel())
        // check if there are any remaining bytes in the session buffe
        if (!session!!.isBufferEmpty()) {
            // forward one packet from the buffer to destination
            session.writeAndFlush(session.getPacket()!!)
        }

    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        Demux.removeSession(ctx.channel())
        ctx.fireChannelInactive()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Demux.removeSession(ctx.channel())
        cause.printStackTrace()
        // Close the connection when an exception is caught, as it might be in an unrecoverable state.
        ctx.close()
    }
}