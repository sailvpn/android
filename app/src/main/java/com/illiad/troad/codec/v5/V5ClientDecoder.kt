package com.illiad.troad.codec.v5

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.ReplayingDecoder
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import io.netty.handler.codec.socksx.v5.Socks5Message

/**
 * Decodes a single [Socks5CommandResponse] from the inbound [ByteBuf]s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later.  On failed decode, this decoder will
 * discard the received data, so that other handler closes the connection later.
 */
class V5ClientDecoder(private val v5AddressDecoder: V5AddressDecoder) :
    ReplayingDecoder<V5ClientDecoder.State?>(
        State.INIT
    ) {
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
                        val status = Socks5CommandStatus.valueOf(buf.readByte())
                        buf.skipBytes(1) // Reserved
                        val addrType = Socks5AddressType.valueOf(buf.readByte())
                        val addr = v5AddressDecoder.decodeAddress(addrType, buf)
                        val port = ByteBufUtil.readUnsignedShortBE(buf)

                        out.add(DefaultSocks5CommandResponse(status, addrType, addr, port))
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
                }

            }
        } catch (e: Exception) {
            fail(out, e)
        }
    }

    private fun fail(out: MutableList<Any?>, cause: Exception?) {

        val err = if (cause is DecoderException) cause else DecoderException(cause)

        checkpoint(State.FAILURE)

        val m: Socks5Message = DefaultSocks5CommandResponse(
            Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4, null, 0
        )
        m.setDecoderResult(DecoderResult.failure(err))
        out.add(m)
    }

    enum class State {
        INIT,
        SUCCESS,
        FAILURE
    }
}
