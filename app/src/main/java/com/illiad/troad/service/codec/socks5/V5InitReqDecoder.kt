package com.illiad.troad.service.codec.socks5

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.ReplayingDecoder
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod
import io.netty.handler.codec.socksx.v5.Socks5Message

/**
 * Decodes a single [Socks5InitialRequest] from the inbound [ByteBuf]s.
 * On successful decode, this decoder will forward the received data to the next handler, On failed decode, this decoder will
 * discard the received data, the decoder remove itself upon exiting.
 */
class V5InitReqDecoder : ReplayingDecoder<V5InitReqDecoder.State?>(State.INIT) {
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

                        val authMethodCnt = buf.readUnsignedByte().toInt()

                        val authMethods = arrayOfNulls<Socks5AuthMethod>(authMethodCnt)
                        var i = 0
                        while (i < authMethodCnt) {
                            authMethods[i] = Socks5AuthMethod.valueOf(buf.readByte())
                            i++
                        }

                        out.add(DefaultSocks5InitialRequest(*authMethods))
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

        val m: Socks5Message = DefaultSocks5InitialRequest(Socks5AuthMethod.NO_AUTH)
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
