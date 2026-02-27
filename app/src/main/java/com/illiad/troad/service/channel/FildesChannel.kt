package com.illiad.troad.service.channel

import android.util.Log
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.util.internal.logging.InternalLoggerFactory
import java.io.FileDescriptor
import java.io.IOException
import java.net.SocketAddress
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit

class FildesChannel(parent: Channel?, private val fd: FileDescriptor) : AbstractChannel(parent) {

    private val logger = InternalLoggerFactory.getInstance(FildesChannel::class.java)
    private val config = FildesChannelConfig(this)

    // Single bidirectional channel for the TUN FD
    private var nioChannel: FileChannel? = null

    @Volatile
    private var isPaused = false
    private val ZERO_READ_PAUSE_MS = 10L

    init {
        try {
            if (!fd.valid()) throw IOException("Invalid FileDescriptor")

            // Use FileInputStream so the NIO FileChannel marks itself as READABLE.
            // Because the TUN FD is bidirectional, this channel WILL still allow writes.
            this.nioChannel = java.io.FileInputStream(fd).channel

            Log.d("FildesChannel", "NIO Channel initialized from FileInputStream for R/W")
        } catch (e: Exception) {
            Log.e("FildesChannel", "Failed to initialize NIO channel from FD", e)
            close(voidPromise())
        }
    }

    override fun config(): FildesChannelConfig = config
    override fun metadata(): ChannelMetadata = ChannelMetadata(false)
    override fun isRegistered(): Boolean = super.isRegistered()
    override fun isActive(): Boolean = fd.valid() && nioChannel?.isOpen == true
    override fun isOpen(): Boolean = fd.valid()
    override fun isCompatible(loop: EventLoop?): Boolean {
        return loop is EventLoop
    }


    // --- Outbound Logic: Injecting packets back into TUN ---
    override fun doWrite(buf: ChannelOutboundBuffer) {
        val channel = nioChannel ?: return

        while (true) {
            val msg = buf.current() ?: break
            if (msg is ByteBuf) {
                try {
                    val localBuf = msg.nioBuffer()
                    val written = channel.write(localBuf)
                    if (written <= 0) break // TUN is temporarily full
                    buf.remove() // Successfully injected packet
                } catch (e: Exception) {
                    buf.remove(e)
                }
            } else {
                buf.remove(UnsupportedOperationException("Only ByteBuf is supported"))
            }
        }
    }

    override fun doBeginRead() {
        // This is called by the pipeline or by Unsafe.beginRead()
        // We delegate to our internal unsafe to perform the actual I/O
        (unsafe() as FildesUnsafe).readFromFildes()
    }

    // --- Inbound Logic: Captured through this custom Unsafe ---
    override fun newUnsafe(): AbstractUnsafe = FildesUnsafe()

    private inner class FildesUnsafe : AbstractUnsafe() {

        // Rename this from 'beginRead' to something unique like 'readFromFildes'
        fun readFromFildes() {
            if (isPaused || !isActive) return

            val allocHandle = recvBufAllocHandle()
            allocHandle.reset(config())
            val allocator = config().allocator
            var firedRead = false

            try {
                do {
                    val byteBuf = allocHandle.allocate(allocator)
                    // Use nioChannel directly
                    val bytesRead = byteBuf.writeBytes(nioChannel!!, byteBuf.writableBytes())
                    allocHandle.lastBytesRead(bytesRead)

                    if (bytesRead <= 0) {
                        byteBuf.release()
                        if (bytesRead < 0) {
                            close(voidPromise()) // EOF
                        } else {
                            handleZeroRead() // 0 bytes available
                        }
                        break
                    }

                    firedRead = true
                    allocHandle.incMessagesRead(1)
                    pipeline().fireChannelRead(byteBuf)

                } while (allocHandle.continueReading())

            } catch (t: Throwable) {
                pipeline().fireExceptionCaught(t)
            } finally {
                allocHandle.readComplete()
                if (firedRead) {
                    pipeline().fireChannelReadComplete()
                }
            }
        }

        private fun handleZeroRead() {
            isPaused = true
            eventLoop().schedule({
                isPaused = false
                if (config().isAutoRead) {
                    // Call the top-level doBeginRead again
                    this@FildesChannel.doBeginRead()
                }
            }, ZERO_READ_PAUSE_MS, TimeUnit.MILLISECONDS)
        }

        // Required override for AbstractUnsafe
        override fun connect(
            remote: SocketAddress?,
            local: SocketAddress?,
            promise: ChannelPromise?
        ) {
            promise?.setFailure(UnsupportedOperationException("TUN is point-to-point"))
        }
    }

    override fun doClose() {
        nioChannel?.close()
    }

    override fun doBind(localAddress: SocketAddress?) {}
    override fun doDisconnect() {
        doClose()
    }

    override fun localAddress0(): SocketAddress? = null
    override fun remoteAddress0(): SocketAddress? = null
}


