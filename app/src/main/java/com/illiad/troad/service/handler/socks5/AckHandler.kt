package com.illiad.troad.service.handler.socks5

import com.illiad.troad.service.HandlerNamer
import com.illiad.troad.service.Utils.closeOnFlush
import com.illiad.troad.service.handler.ip.Connection
import com.illiad.troad.service.handler.ip.Demux
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import java.util.concurrent.ExecutionException

class AckHandler(private val frontendCtx: ChannelHandlerContext, private val connection: Connection) :
    SimpleChannelInboundHandler<Socks5CommandResponse?>() {
    @Throws(ExecutionException::class, InterruptedException::class)
    override fun channelRead0(ctx: ChannelHandlerContext?, response: Socks5CommandResponse?) {

        if (ctx == null || response == null) {
            Demux.removeSession(connection)
            closeOnFlush(ctx?.channel()!!)
            return
        }
        if (response.status() === Socks5CommandStatus.SUCCESS) {
            // frontend refers to the DemuxHandler
            val frontend = frontendCtx.channel()!!
            val frontendPipeline = frontend.pipeline()
            val backend = ctx.channel()!!
            val backendPipeline = backend.pipeline()
            // setup Socks direct channel relay for backend
            backendPipeline.addLast(RelayHandler())
            val prefix: String = HandlerNamer.prefix
            // remove all handlers except SslHandler from backendPipeline
            for (name in backendPipeline.names()) {
                if (name.startsWith(prefix)) {
                    backendPipeline.remove(name)
                }
            }
            // remove all handlers except LoggingHandler from frontendPipeline
            for (name in frontendPipeline.names()) {
                if (name.startsWith(prefix)) {
                    frontendPipeline.remove(name)
                }
            }

            // associate backend channel with connection, now that it is established
            val session = Demux.getSession(connection);
            if (session != null) {
                session.setChannel(backend)
                // forward the first packet to the backend, to
                if(!session.isBufferEmpty()){
                    session.writeAndFlush(session.getPacket()!!)
                }
            } else {
                // this actually should never have happend
                // we should never close frontend(Demux)here,
                // we just close the backend which is an individual session
                Demux.removeSession(connection)
                closeOnFlush(ctx.channel()!!)
            }

        } else {
            // we should never close frontend(Demux)here,
            // we just close the backend which is an individual session
            Demux.removeSession(connection)
            closeOnFlush(ctx.channel()!!)
        }
    }

}
