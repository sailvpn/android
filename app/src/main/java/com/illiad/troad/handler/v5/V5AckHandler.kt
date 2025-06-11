package com.illiad.troad.handler.v5

import com.illiad.troad.HandlerNamer
import com.illiad.troad.handler.Utils.closeOnFlush
import com.illiad.troad.handler.RelayHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import java.util.concurrent.ExecutionException

class V5AckHandler(private val frontendCtx: ChannelHandlerContext) :
    SimpleChannelInboundHandler<Socks5CommandResponse?>() {
    @Throws(ExecutionException::class, InterruptedException::class)
    override fun channelRead0(ctx: ChannelHandlerContext?, response: Socks5CommandResponse?) {
        if (response?.status() === Socks5CommandStatus.SUCCESS) {
            // write response to frontend
            val frontend = frontendCtx.channel()!!
            frontend.writeAndFlush(response)
                .addListener(GenericFutureListener { future: Future<in Void?>? ->
                    if (future!!.isSuccess()) {
                        val frontendPipeline = frontend.pipeline()
                        val backend = ctx?.channel()!!
                        val backendPipeline = backend.pipeline()
                        // setup Socks direct channel relay between frontend and backend
                        frontendPipeline.addLast(RelayHandler(backend))
                        backendPipeline.addLast(RelayHandler(frontend))
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
                    } else {
                        frontendCtx.fireExceptionCaught(future.cause())
                        closeOnFlush(frontend)
                        closeOnFlush(ctx?.channel()!!)
                    }
                })
        } else {
            frontendCtx.fireExceptionCaught(Exception(response?.status().toString()))
            closeOnFlush(frontendCtx.channel())
            closeOnFlush(ctx?.channel()!!)
        }
    }

}

