package com.illiad.troad.service.codec.socks5

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.ReplayingDecoder
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.handler.codec.socksx.v5.Socks5CommandType
import io.netty.handler.codec.socksx.v5.Socks5Message

/**
 * Decodes a single [Socks5CommandRequest] from the inbound [ByteBuf]s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later.  On failed decode, this decoder will
 * discard the received data, so that other handler closes the connection later.
 */
class V5CmdReqDecoder : ReplayingDecoder<V5CmdReqDecoder.State?>(State.INIT) {
    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any?>) {
        try {
            when (state()!!) {
                State.INIT -> {
                    run {
                        val version = buf.readByte()
                        if (version != SocksVersion.SOCKS5.byteValue()) {
                            throw DecoderException(
                                "unsupported version: " + version + " (expected: " + SocksVersion.SOCKS5.byteValue() + ')'
                            )
                        }

                        val type = Socks5CommandType.valueOf(buf.readByte())
                        buf.skipBytes(1) // RSV
                        val dstAddrType = Socks5AddressType.valueOf(buf.readByte())
                        val dstAddr: String = V5AddressDecoder.decodeAddress(dstAddrType, buf)!!
                        val dstPort = ByteBufUtil.readUnsignedShortBE(buf)

                        out.add(DefaultSocks5CommandRequest(type, dstAddrType, dstAddr, dstPort))
                        checkpoint(State.SUCCESS)
                    }
                    run {
                        val readableBytes = actualReadableBytes()
                        if (readableBytes > 0) {
                            out.add(buf.readRetainedSlice(readableBytes))
                        }
                        ctx.pipeline().remove(this)
                    }
                }

                State.SUCCESS -> {
                    val readableBytes = actualReadableBytes()
                    if (readableBytes > 0) {
                        out.add(buf.readRetainedSlice(readableBytes))
                    }
                    ctx.pipeline().remove(this)
                }

                State.FAILURE -> {
                    buf.skipBytes(actualReadableBytes())
                    ctx.pipeline().remove(this)
                }
            }
        } catch (e: Exception) {
            fail(out, e)
        }
    }

    private fun fail(out: MutableList<Any?>, cause: Exception?) {
        checkpoint(State.FAILURE)

        val m: Socks5Message = DefaultSocks5CommandRequest(
            Socks5CommandType.CONNECT, Socks5AddressType.IPv4, "0.0.0.0", 1
        )
        if (cause is DecoderException) {
            m.setDecoderResult(DecoderResult.failure(cause))
        } else {
            m.setDecoderResult(DecoderResult.failure(DecoderException(cause)))
        }
        out.add(m)
    }

    enum class State {
        INIT,
        SUCCESS,
        FAILURE
    }
}
