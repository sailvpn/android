package com.illiad.troad.service.handler.socks5.udp

import com.illiad.troad.service.HandlerNamer
import com.illiad.troad.service.Utils
import com.illiad.troad.service.handler.ip.Connection
import com.illiad.troad.service.handler.ip.Demux
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import java.util.concurrent.ExecutionException

class AsoAckHandler(private val frontendCtx: ChannelHandlerContext, private val connection: Connection) :
    SimpleChannelInboundHandler<Socks5CommandResponse?>() {
    @Throws(ExecutionException::class, InterruptedException::class)
    override fun channelRead0(ctx: ChannelHandlerContext?, response: Socks5CommandResponse?) {

        if (ctx == null || response == null) {
            Demux.removeSession(connection)
            Utils.closeOnFlush(ctx?.channel()!!)
            return
        }
        if (response.status() == Socks5CommandStatus.SUCCESS) {
            val aso = ctx.channel()!!
            val asoPipeline = aso.pipeline()

            val prefix: String = HandlerNamer.prefix
            // remove all handlers except SslHandler from asoPipeline
            for (name in asoPipeline.names()) {
                if (name.startsWith(prefix)) {
                    asoPipeline.remove(name)
                }
            }

            // associate aso channel with connection, now that it is established
            val session = Demux.getSession(connection);
            if (session != null) {
                session.aso = aso
                // forward the first packet to the backend
                if(session.isBufferEmpty() != true){
                    session.writeAndFlush(session.getPacket()!!)
                }
            } else {
                // this actually should never have happend
                // we should never close frontend(Demux)here,
                // we just close the backend which is an individual session
                Demux.removeSession(connection)
                Utils.closeOnFlush(ctx.channel()!!)
            }

        } else {
            // we should never close frontend(Demux)here,
            // we just close the backend which is an individual session
            Demux.removeSession(connection)
            Utils.closeOnFlush(ctx.channel()!!)
        }
    }

}