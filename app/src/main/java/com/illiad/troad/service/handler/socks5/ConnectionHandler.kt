package com.illiad.troad.service.handler.socks5

import com.illiad.troad.service.HandlerNamer
import com.illiad.troad.service.Utils.closeOnFlush
import com.illiad.troad.service.codec.socks5.V5ClientDecoder
import com.illiad.troad.service.codec.socks5.V5ClientEncoder
import com.illiad.troad.service.handler.ip.Connection
import com.illiad.troad.service.security.Ssl
import com.illiad.troad.service.Utils.serverDomain
import com.illiad.troad.service.Utils.serverPort
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.handler.codec.socksx.v5.Socks5CommandType
import io.netty.handler.ssl.SslHandler
import io.netty.util.concurrent.GenericFutureListener
import io.netty.util.concurrent.Future

class ConnectionHandler() : SimpleChannelInboundHandler<Connection>() {
    private val b = Bootstrap()

    public override fun channelRead0(ctx: ChannelHandlerContext, connection: Connection) {
        val commandType = when (connection.protocol) {
            "TCP" -> Socks5CommandType.valueOf(1)
            "UDP" -> Socks5CommandType.valueOf(3)
            else -> {
                ctx.fireExceptionCaught(Exception("Unknown protocol: ${connection.protocol}"))
                return
            }
        }

        val aType = when (connection.ipVersion) {
            4 -> Socks5AddressType.valueOf(1)
            6 -> Socks5AddressType.valueOf(4)
            else -> {
                ctx.fireExceptionCaught(Exception("Unknown IP version: ${connection.ipVersion}"))
                return
            }
        }
        val request = DefaultSocks5CommandRequest(
            commandType,
            aType,
            connection.destinationAddress.toString(),
            connection.destinationPort!!
        )
        b.group(ctx.channel().eventLoop()).channel(NioSocketChannel::class.java)
            .option<Int?>(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .option<Boolean?>(ChannelOption.SO_KEEPALIVE, true)
            .handler(object : ChannelInitializer<SocketChannel?>() {
                override fun initChannel(sc: SocketChannel?) {}
            }) // connect to the proxy server, and forward the Socks connect command message to the remote server
            .connect(serverDomain, serverPort)
            .addListener(ChannelFutureListener { future: ChannelFuture? ->
                if (future!!.isSuccess) {
                    val ch = future.channel()
                    val sslHandler: SslHandler = Ssl.sslCtx!!.newHandler(
                        ch.alloc(),
                        serverDomain,
                        serverPort
                    )
                    val pipeline = ch.pipeline()
                    pipeline.addLast(sslHandler)
                    // Add a listener for the SSL handshake
                    sslHandler.handshakeFuture()
                        .addListener(GenericFutureListener { future1: Future<in Channel?>? ->
                            if (future1!!.isSuccess) {
                                // backend outbound encoder: standard socks5 command request (Connect or UdP)
                                pipeline.addLast(HandlerNamer.name, V5ClientEncoder)
                                    // backend inbound decoder: socks5 client decoder
                                    .addLast(HandlerNamer.name, V5ClientDecoder())
                                    .addLast(HandlerNamer.name, AckHandler(ctx, connection))
                                    .channel().writeAndFlush(request)
                                    .addListener(ChannelFutureListener { future2: ChannelFuture? ->
                                        if (!future2!!.isSuccess) {
                                            ctx.fireExceptionCaught(Exception(future2.cause()))
                                            closeOnFlush(ch)
                                            closeOnFlush(ctx.channel())
                                        }
                                    })
                            } else {
                                ctx.fireExceptionCaught(Exception(future1.cause()))
                                closeOnFlush(ch)
                                closeOnFlush(ctx.channel()) // Close the channel on failure
                            }
                        })
                } else {
                    // Close the connection if the connection attempt has failed.
                    ctx.fireExceptionCaught(future.cause())
                    closeOnFlush(future.channel())
                    closeOnFlush(ctx.channel())
                }
            })
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        closeOnFlush(ctx.channel())
        ctx.fireExceptionCaught(cause)
    }
}
