package com.illiad.troad.service.handler

import com.illiad.troad.service.HandlerNamer
import com.illiad.troad.service.codec.socks5.V5InitReqDecoder
import com.illiad.troad.service.codec.socks5.V5ServerEncoder
import com.illiad.troad.service.handler.socks5.V5CommandHandler
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.util.internal.logging.InternalLogger
import io.netty.util.internal.logging.InternalLoggerFactory

class VersionHandler : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any?>?) {
        val readerIndex = buf.readerIndex()
        if (buf.writerIndex() == readerIndex) {
            return
        }

        val p = ctx.pipeline()
        val versionVal = buf.getByte(readerIndex)
        val version = SocksVersion.valueOf(versionVal)

        // only socks 5 is supported
        if (version == SocksVersion.SOCKS5) {
            logKnownVersion(ctx, version)
            p.addLast(HandlerNamer.name, V5ServerEncoder())
            p.addLast(HandlerNamer.name, V5InitReqDecoder())
            p.addLast(HandlerNamer.name, V5CommandHandler())
        } else {
            logUnknownVersion(ctx, versionVal)
            buf.skipBytes(buf.readableBytes())
            ctx.close()
            return
        }

        p.remove(this)
    }

    companion object {
        private val logger: InternalLogger =
            InternalLoggerFactory.getInstance(VersionHandler::class.java)

        private fun logKnownVersion(ctx: ChannelHandlerContext, version: SocksVersion?) {
            logger.debug("{} Protocol version: {}({})", ctx.channel(), version)
        }

        private fun logUnknownVersion(ctx: ChannelHandlerContext, versionVal: Byte) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                    "{} Unknown protocol version: {}",
                    ctx.channel(),
                    versionVal.toInt() and 0xFF
                )
            }
        }
    }
}
