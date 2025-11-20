package com.illiad.troad.service.handler.socks5

import com.illiad.troad.service.HandlerNamer
import com.illiad.troad.service.codec.socks5.V5ClientDecoder
import com.illiad.troad.service.codec.socks5.V5ClientEncoder
import com.illiad.troad.service.handler.ip.Connection
import com.illiad.troad.service.security.Ssl
import com.illiad.troad.service.Utils.serverDomain
import com.illiad.troad.service.Utils.serverPort
import com.illiad.troad.service.handler.ip.Demux
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.handler.codec.socksx.v5.Socks5CommandType
import io.netty.util.concurrent.GenericFutureListener
import io.netty.util.concurrent.Future
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ConnectHandler() : SimpleChannelInboundHandler<Connection>() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    public override fun channelRead0(ctx: ChannelHandlerContext, connection: Connection) {

        serviceScope.launch {

            val aType = when (connection.ipVersion) {
                4 -> Socks5AddressType.valueOf(1)
                6 -> Socks5AddressType.valueOf(4)
                else -> {
                    return@launch
                }
            }
            val request = DefaultSocks5CommandRequest(
                Socks5CommandType.CONNECT,
                aType,
                connection.dst.hostAddress,
                connection.dstPort
            )

            val b = Bootstrap()
            val group = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())
            b.group(group).channel(NioSocketChannel::class.java)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(object : ChannelInitializer<SocketChannel?>() {
                    override fun initChannel(sc: SocketChannel?) {}
                }) // connect to the proxy server, and forward the Socks connect command message to the remote server
                .connect(serverDomain, serverPort)
                .addListener(ChannelFutureListener { future: ChannelFuture? ->
                    if (future!!.isSuccess) {
                        val ch = future.channel()
                        val sslHandler = Ssl.sslCtx!!.newHandler(
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
                                                Demux.removeSession(connection)
                                            }
                                        })
                                } else {
                                    Demux.removeSession(connection)
                                }
                            })
                    } else {
                        Demux.removeSession(connection)

                    }
                })
        }
        ctx.pipeline().remove(this)
    }

}
