package com.illiad.troad.service.handler.socks5

import com.illiad.troad.service.HandlerNamer
import com.illiad.troad.service.Utils.closeOnFlush
import com.illiad.troad.service.codec.socks5.V5CmdReqDecoder
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandType
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest
import io.netty.handler.codec.socksx.v5.Socks5Message
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus

class V5CommandHandler : SimpleChannelInboundHandler<Socks5Message?>() {

    public override fun channelRead0(ctx: ChannelHandlerContext, socksRequest: Socks5Message?) {
        if (socksRequest is Socks5InitialRequest) {
            // auth support example
            //ctx.pipeline().addFirst(new V5PwdAuthReqDecoder());
            //ctx.write(new DefaultSocks5AuthMethodResponse(Socks5AuthMethod.PASSWORD));
            ctx.pipeline().addFirst(HandlerNamer.name, V5CmdReqDecoder())
            ctx.write(DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
        } else if (socksRequest is Socks5PasswordAuthRequest) {
            ctx.pipeline().addFirst(HandlerNamer.name, V5CmdReqDecoder())
            ctx.write(DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS))
        } else if (socksRequest is Socks5CommandRequest) {
            if (socksRequest.type() == Socks5CommandType.CONNECT) {
                ctx.pipeline().addLast(HandlerNamer.name, V5ConnectHandler())
                ctx.pipeline().remove(this)
                ctx.fireChannelRead(socksRequest)
            } else {
                ctx.close()
            }
        } else {
            ctx.close()
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, throwable: Throwable?) {
        ctx.fireExceptionCaught(throwable)
        closeOnFlush(ctx.channel())
    }
}


