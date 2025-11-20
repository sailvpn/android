package com.illiad.troad.service.handler.socks5.udp

import com.illiad.troad.service.HandlerNamer
import com.illiad.troad.service.Utils
import com.illiad.troad.service.handler.ip.Connection
import com.illiad.troad.service.handler.ip.Demux
import com.illiad.troad.service.security.Dtls
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import java.util.concurrent.ExecutionException


class AsoAckHandler(private val connection: Connection) :
    SimpleChannelInboundHandler<Socks5CommandResponse?>() {

    @Throws(ExecutionException::class, InterruptedException::class)
    override fun channelRead0(ctx: ChannelHandlerContext?, res: Socks5CommandResponse?) {

        if (ctx == null || res == null) {
            Demux.removeSession(connection)
            Utils.closeOnFlush(ctx?.channel()!!)
            return
        }
        if (res.status() == Socks5CommandStatus.SUCCESS) {
            val asoPipeline = ctx.channel().pipeline()
            val prefix: String = HandlerNamer.prefix
            // remove all handlers except SslHandler from asoPipeline
            for (name in asoPipeline.names()) {
                if (name.startsWith(prefix)) {
                    asoPipeline.remove(name)
                }
            }
            val session = Demux.getSession(connection);
            if (session != null) {
                // associate aso channel with connection, now that it is established
                session.aso = ctx.channel()

                Bootstrap().group(ctx.channel().eventLoop())
                    .channel(NioDatagramChannel::class.java)
                    // Enable broadcasting if needed
                    .option(ChannelOption.SO_BROADCAST, true)
                    .handler(object : ChannelInitializer<NioDatagramChannel?>() {
                        override fun initChannel(ch: NioDatagramChannel?) {}
                    })
                    .connect(res.bndAddr(), res.bndPort())
                    .addListener(ChannelFutureListener { future: ChannelFuture ->
                        if (future.isSuccess) {
                            val ch = future.channel()
                            // associate udp relay channel with connection
                            session.channel = ch
                            val pipeline = ch.pipeline()
                            // setup DTLS handlers for backend
                            val dtlsHandler = Dtls.dtlsCtx!!.newHandler(
                                ch.alloc(),
                                res.bndAddr(),
                                res.bndPort(),
                            )
                            pipeline.addLast(dtlsHandler)
                            dtlsHandler.handshakeFuture()
                                .addListener(GenericFutureListener { future1: Future<*> ->
                                    if (future1.isSuccess) {
                                        pipeline.addLast(ResHandler())
                                        ch.writeAndFlush(session.getPacket())
                                    } else {
                                        Demux.removeSession(connection)
                                    }
                                })

                        } else {
                            Demux.removeSession(connection)
                        }
                    })
            } else {
                // this actually should never have happend
                // we should never close frontend(Demux)here,
                // we just close the backend which is an individual session
                Demux.removeSession(connection)
            }

        } else {
            // we should never close frontend(Demux)here,
            // we just close the backend which is an individual session
            Demux.removeSession(connection)
        }
    }

}