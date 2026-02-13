package com.illiad.troad.service.handler.dtls

import android.util.Log
import com.illiad.troad.Consts.APP_IN_SIZE
import com.illiad.troad.Consts.DH
import com.illiad.troad.Consts.FRAGMENT_SIZE
import com.illiad.troad.Consts.NET_IN_SIZE
import com.illiad.troad.Consts.NET_OUT_SIZE
import com.illiad.troad.service.security.CertManager
import io.netty.channel.*
import io.netty.channel.socket.DatagramPacket
import io.netty.util.concurrent.DefaultPromise
import io.netty.util.concurrent.EventExecutor
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.Promise
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.net.ssl.*
import javax.net.ssl.SSLEngineResult.HandshakeStatus.*

/**
 * A Netty handler that manages DTLS communication over UDP using SSLEngine.
 */
class DtlsHandler(remoteAddress: InetSocketAddress) : ChannelDuplexHandler() {

    private val sslEngine: SSLEngine = CertManager.dtlsCtx!!.createSSLEngine(remoteAddress.hostString, remoteAddress.port).apply {
        useClientMode = true
    }

    private val netin: ByteBuffer = ByteBuffer.allocate(NET_IN_SIZE)
    private val appin: ByteBuffer = ByteBuffer.allocate(APP_IN_SIZE)
    private val netout: ByteBuffer = ByteBuffer.allocate(NET_OUT_SIZE)
    private val emptyBuffer: ByteBuffer = ByteBuffer.allocate(0)

    private var netinWriteMode = true
    private var netoutWriteMode = true

    private val handshakePromise: Promise<Channel> = LazyChannelPromise()
    private var context: ChannelHandlerContext? = null

    private val inboundLock = Any()
    private val outboundLock = Any()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        this.context = ctx
        try {
            sslEngine.beginHandshake()
            sendHandshake()
        } catch (e: SSLException) {
            ctx.fireExceptionCaught(e)
        }

        // Add handshake timeout
        ctx.executor().schedule({
            if (!handshakePromise.isDone) {
                val timeout = SSLHandshakeException("DTLS handshake timeout")
                handshakePromise.setFailure(timeout)
                Log.e(DH, "DTLS handshake timeout")
                ctx.close()
            }
        }, 30, TimeUnit.SECONDS)
    }

    private fun sendHandshake() {
        synchronized(outboundLock) {
            try {
                val status = sslEngine.wrap(emptyBuffer, netout).status
                if (status == SSLEngineResult.Status.OK) {
                    val ctx = context ?: return
                    doSend(ctx.channel().remoteAddress() as InetSocketAddress, ctx.channel().localAddress() as InetSocketAddress)
                }
            } catch (e: SSLException) {
                context?.fireExceptionCaught(e)
            }
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is DatagramPacket) {
            append(msg)
            if (sslEngine.handshakeStatus == NOT_HANDSHAKING) {
                decrypt(msg.recipient(), msg.sender())
            } else {
                doHandshake(msg.sender(), msg.recipient())
            }
        }
    }

    private fun append(packet: DatagramPacket) {
        synchronized(inboundLock) {
            if (!netinWriteMode) {
                netin.compact()
                netinWriteMode = true
            }
            netin.put(packet.content().nioBuffer())
            packet.content().release()
        }
    }

    private fun decrypt(recipient: InetSocketAddress, sender: InetSocketAddress) {
        val ctx = context ?: return
        synchronized(inboundLock) {
            if (netinWriteMode) {
                netin.flip()
                netinWriteMode = false
            }
            try {
                val result = sslEngine.unwrap(netin, appin)
                appin.flip()
                if (result.handshakeStatus == NOT_HANDSHAKING) {
                    when (result.status) {
                        SSLEngineResult.Status.OK -> if (appin.hasRemaining()) {
                            val buf = ctx.alloc().buffer(appin.remaining())
                            buf.writeBytes(appin)
                            ctx.fireChannelRead(DatagramPacket(buf, recipient, sender))
                        }
                        SSLEngineResult.Status.BUFFER_OVERFLOW ->
                            throw SSLException("Application buffer overflow - increase APP_IN_SIZE")
                        SSLEngineResult.Status.CLOSED -> {
                            Log.w(DH, "SSL engine closed during decrypt")
                            ctx.close()
                        }
                        else -> {} // UNDERFLOW
                    }
                }
                appin.clear()
            } catch (e: SSLException) {
                Log.e(DH,"Decryption error", e)
                ctx.fireExceptionCaught(e)
                ctx.close()
            }
        }
    }

    /**
     * Performs the DTLS handshake.
     * HandshakeStatus "NEED_UNWRAP_AGAIN" was removed as per android
     * @param recipient The remote address.
     * @param sender The local address.
     */
    private fun doHandshake(recipient: InetSocketAddress, sender: InetSocketAddress) {
        val ctx = context ?: return
        try {
            var hsStatus = sslEngine.handshakeStatus
            while (hsStatus != NOT_HANDSHAKING) {
                when (hsStatus) {
                    NEED_UNWRAP -> synchronized(inboundLock) {
                        if (netinWriteMode) {
                            netin.flip()
                            netinWriteMode = false
                        }

                        // Android loop: unwrap until the engine needs more network data (UNDERFLOW)
                        // or changes state to NEED_WRAP/NEED_TASK.
                        while (sslEngine.handshakeStatus == NEED_UNWRAP) {
                            val result = sslEngine.unwrap(netin, appin)
                            when (result.status) {
                                SSLEngineResult.Status.BUFFER_UNDERFLOW -> return // Exit and wait for packet
                                SSLEngineResult.Status.BUFFER_OVERFLOW ->
                                    throw SSLException("Handshake buffer overflow - increase APP_IN_SIZE")
                                SSLEngineResult.Status.CLOSED ->
                                    throw SSLException("SSL engine closed during handshake")
                                SSLEngineResult.Status.OK -> {
                                    // If no bytes consumed but state is still UNWRAP, it's an
                                    // internal flight processing (the old NEED_UNWRAP_AGAIN).
                                    if (result.bytesConsumed() == 0 && sslEngine.handshakeStatus == NEED_UNWRAP) {
                                        sslEngine.unwrap(emptyBuffer, appin)
                                    }
                                }
                            }
                        }
                    }

                    NEED_WRAP -> synchronized(outboundLock) {
                        val wStatus = sslEngine.wrap(emptyBuffer, netout).status
                        when (wStatus) {
                            SSLEngineResult.Status.OK -> doSend(recipient, sender)
                            SSLEngineResult.Status.BUFFER_OVERFLOW ->
                                throw SSLException("Handshake output buffer overflow - increase NET_OUT_SIZE")
                            SSLEngineResult.Status.CLOSED ->
                                throw SSLException("SSL engine closed during handshake")
                            else -> {}
                        }
                    }

                    NEED_TASK -> {
                        var task: Runnable?
                        while (sslEngine.delegatedTask.also { task = it } != null) {
                            task?.run()
                        }
                    }
                    else -> {}
                }
                hsStatus = sslEngine.handshakeStatus
            }

            // Handshake completed successfully
            if (hsStatus == NOT_HANDSHAKING) {
                synchronized(inboundLock) {
                    netin.clear()
                    appin.clear()
                    netinWriteMode = true
                }
                Log.i(DH, "DTLS handshake completed")
                handshakePromise.setSuccess(ctx.channel())
            }
        } catch (e: SSLException) {
            Log.e(DH, "DTLS handshake failed", e)
            handshakePromise.setFailure(e)
            ctx.fireExceptionCaught(e)
            ctx.close()
        }
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg !is DatagramPacket) {
            ctx.write(msg, promise)
            return
        }
        synchronized(outboundLock) {
            try {
                if (sslEngine.handshakeStatus != NOT_HANDSHAKING) {
                    promise.setFailure(IllegalStateException("Handshake not completed"))
                    msg.content().release()
                    return
                }
                val status = sslEngine.wrap(msg.content().nioBuffer(), netout).status
                msg.content().release()
                if (status == SSLEngineResult.Status.OK) {
                    doSend(msg.recipient(), msg.sender())
                    promise.setSuccess()
                } else {
                    promise.setFailure(SSLException("Encryption failed: $status"))
                }
            } catch (e: SSLException) {
                promise.setFailure(e)
                ctx.fireExceptionCaught(e)
            }
        }
    }

    private fun doSend(recipient: InetSocketAddress, sender: InetSocketAddress) {
        val ctx = context ?: return
        if (netoutWriteMode) {
            netout.flip()
            netoutWriteMode = false
        }
        while (netout.hasRemaining()) {
            val fragmentSize = netout.remaining().coerceAtMost(FRAGMENT_SIZE)
            val slice = netout.slice()
            slice.limit(fragmentSize)
            val buf = ctx.alloc().buffer(fragmentSize)
            buf.writeBytes(slice)
            netout.position(netout.position() + fragmentSize)
            ctx.write(DatagramPacket(buf, recipient, sender))
        }
        ctx.flush()
        netout.clear()
        netoutWriteMode = true
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        try {
            sslEngine.closeOutbound()
        } catch (e: Exception) {
            Log.w(DH, "Error closing SSL engine", e)
        }
        ctx.fireChannelInactive()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.e(DH, "Exception in DTLS handler", cause)
        ctx.fireExceptionCaught(cause)
        ctx.close()
    }

    fun handshakeFuture(): Future<Channel> = handshakePromise

    private inner class LazyChannelPromise : DefaultPromise<Channel>() {
        override fun executor(): EventExecutor {
            val ctx = context ?: throw IllegalStateException("Handler not added to pipeline")
            return ctx.executor()
        }

        override fun checkDeadLock() {
            if (context == null) return
            super.checkDeadLock()
        }
    }
}
