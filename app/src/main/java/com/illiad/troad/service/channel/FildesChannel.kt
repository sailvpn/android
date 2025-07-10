package com.illiad.troad.service.channel // Your package

import FildesAddress
import io.netty.channel.*
import io.netty.buffer.ByteBuf
import io.netty.util.internal.logging.InternalLoggerFactory
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketAddress
import java.nio.channels.FileChannel as NioFileChannel // Alias to avoid clash

class FildesChannel(parent: Channel?, private val fd: FileDescriptor) : AbstractChannel(parent) {

    private val logger = InternalLoggerFactory.getInstance(FildesChannel::class.java)
    private val config = FildesChannelConfig(this)

    // Represents the underlying NIO FileChannel for actual I/O.
    // We get separate channels for input and output from the same FD.
    private var nioInputStreamChannel: NioFileChannel? = null
    private var nioOutputStreamChannel: NioFileChannel? = null

    @Volatile
    private var inputShutdown = false // To manage half-closure for input
    @Volatile
    private var outputShutdown = false // To manage half-closure for output

    // The 'active' state for this channel means the fd is valid and we've "connected"
    // (i.e., initialized our nio channels for it).
    @Volatile
    private var channelActive = false


    // The fildesAddress is constant for this channel instance.
    private val fildesAddress = FildesAddress(fd)

    init {
        // Primary initialization. Consider if 'connect' is a more appropriate place
        // for opening nioInputStreamChannel and nioOutputStreamChannel.
        // For now, let's assume opening them makes the channel "open".
        try {
            if (!fd.valid()) {
                throw IOException("FileDescriptor is invalid")
            }
            // Note: A single FileDescriptor can be used for both reading and writing
            // if it was opened with appropriate permissions.
            // FileInputStream/FileOutputStream create their own FDs if given a path,
            // but can also wrap an existing FD.
            this.nioInputStreamChannel = FileInputStream(fd).channel
            this.nioOutputStreamChannel = FileOutputStream(fd).channel
            // If the FD is for read-only, FileOutputStream(fd) might fail, and vice-versa.
            // You'll need to handle this based on how the FD was created.
            // For simplicity, assuming fd is R/W.
        } catch (e: IOException) {
            // If we can't open, this channel instance is fundamentally broken.
            // This might be better handled in `doConnect` and failing the connect promise.
            // For now, let `AbstractChannel` handle it if `isOpen` returns false.
            logger.warn("Failed to initialize NIO FileChannels from FD", e)
            // Ensure they are null if initialization failed
            this.nioInputStreamChannel = null
            this.nioOutputStreamChannel = null
        }
    }

    override fun newUnsafe(): AbstractUnsafe = FildesChannelUnsafe()
    override fun config(): ChannelConfig = config
    override fun metadata(): ChannelMetadata = ChannelMetadata(false) // false because it's connection-oriented (once "connected")

    override fun isOpen(): Boolean {
        // Open if the underlying FD is valid and at least one of the NIO channels could be opened.
        // Or, more strictly, if the resources needed for operation are available.
        return fd.valid() && (nioInputStreamChannel?.isOpen == true || nioOutputStreamChannel?.isOpen == true)
    }

    override fun isActive(): Boolean {
        // Active could mean it's "connected" or "bound" and ready for I/O.
        // For this channel, let's say it's active if isOpen and our internal active flag is set.
        return isOpen && channelActive
    }

    override fun isCompatible(loop: EventLoop?): Boolean {
        // Typically true if you don't have specific event loop requirements
        return true
    }

    override fun localAddress0(): SocketAddress? {
        return fildesAddress
    }

    override fun remoteAddress0(): SocketAddress? {
        // A FileDescriptor doesn't have a "remote" peer in the network sense.
        // If this channel were to connect TO another FildesAddress, this would be set.
        // For now, let's assume it doesn't connect to a remote FD.
        return null // Or potentially a "peer" FildesAddress if your model supports it
    }

    override fun doBind(localAddress: SocketAddress?) {
        // "Binding" for a FileDescriptor might mean ensuring it's valid and ready.
        // The `localAddress` parameter would be our `FildesAddress`.
        if (localAddress !is FildesAddress || localAddress.fd != this.fd) {
            throw IllegalArgumentException("localAddress must be the FildesAddress of this channel")
        }
        if (!fd.valid()) {
            throw IOException("Cannot bind: FileDescriptor is invalid")
        }
        // If nio channels were not initialized in init, do it here.
        // For this example, let's assume init did it.
        // If binding implies some other setup, do it here.
        logger.debug("{} bound to {}", this, localAddress)
        // `channelActive` might be set here or after a "connect" operation.
        // If bind implies readiness for I/O:
        // this.channelActive = true; (and then fireChannelActive might be triggered by AbstractUnsafe)
    }

    override fun doDisconnect() {
        // "Disconnecting" might mean invalidating the channel for further I/O
        // but not necessarily closing the underlying FD if it's shared.
        // For simplicity, let's treat it like a close.
        doClose()
    }

    override fun doClose() {
        logger.debug("{} closing", this)
        channelActive = false // Mark inactive first

        try {
            nioInputStreamChannel?.close()
        } catch (e: IOException) {
            logger.warn("Failed to close input stream channel for {}: {}", fd, e.message)
        }
        try {
            nioOutputStreamChannel?.close()
        } catch (e: IOException) {
            logger.warn("Failed to close output stream channel for {}: {}", fd, e.message)
        }
        // IMPORTANT: Do NOT close the `fd` (FileDescriptor) itself here unless this
        // FildesChannel exclusively OWNS the fd. If the fd was passed in,
        // the entity that created and passed it should be responsible for its ultimate closure.
        // If FildesChannel *does* own it (e.g., it created the FD from a file path), then close fd.
        logger.info("{} closed", this)
    }

    override fun doBeginRead() {
        if (inputShutdown || !isActive) {
            return
        }

        val nioChannel = nioInputStreamChannel ?: return // Not readable
        val allocHandle = unsafe().recvBufAllocHandle()
        allocHandle.reset(config())

        var continueReading = false
        do {
            val byteBuf = allocHandle.allocate(config().allocator)
            var bytesRead = 0
            var readSuccess = false
            try {
                // Read from nioChannel into byteBuf's underlying ByteBuffer(s)
                bytesRead = byteBuf.writeBytes(nioChannel, allocHandle.attemptedBytesRead())
                if (bytesRead > 0) {
                    readSuccess = true
                    allocHandle.lastBytesRead(bytesRead)
                    allocHandle.incMessagesRead(1) // Assuming one read operation is one "message"
                    pipeline().fireChannelRead(byteBuf) // Pass the read ByteBuf
                } else if (bytesRead == 0 && !byteBuf.isWritable) {
                    // Buffer is full but nothing was read from channel - weird.
                    // This case might mean we need a bigger buffer or should stop.
                    byteBuf.release() // Release if not passed up
                    break
                } else if (bytesRead < 0) { // EOF
                    allocHandle.lastBytesRead(-1) // Signal EOF
                    closeInput() // Or close() if EOF means full closure
                    byteBuf.release() // Release if not passed up
                    break // EOF
                } else { // bytesRead == 0 and buffer still writable
                    byteBuf.release() // Nothing read, release buffer
                    break
                }
            } catch (e: IOException) {
                byteBuf.release()
                pipeline().fireExceptionCaught(e)
                close(voidPromise()) // Close on read error
                return
            }
            // Decide if we should continue reading in this loop
            continueReading = readSuccess && allocHandle.continueReading() && config().isAutoRead

        } while (continueReading)

        pipeline().fireChannelReadComplete()

        // If autoRead is off, or we stopped reading for other reasons,
        // the user will need to call channel.read() again.
    }


    override fun doWrite(buffer: ChannelOutboundBuffer) {
        if (outputShutdown || !isActive) {
            // Drain the buffer and fail promises if not active or output shutdown
            var cause: IOException? = null // To avoid creating multiple exception objects
            while (true) {
                // When removing with an exception, the promise is automatically failed with that cause.
                // buffer.current() is not how you get the message to remove with cause.
                // You remove the head and the promise is handled.
                if (!buffer.remove(IOException("Channel not active or output shutdown"))) {
                    break // Buffer is empty
                }
                // The promise associated with the removed message is failed by buffer.remove(Throwable)
            }
            return
        }

        val nioChannel = nioOutputStreamChannel ?: run {
            // Drain buffer and fail if not writable (e.g., output stream was null or closed)
            while (true) {
                if (!buffer.remove(IOException("Output stream channel is not available"))) {
                    break
                }
            }
            return
        }

        while (true) {
            // Get the current message at the head of the buffer without removing it yet.
            val msg = buffer.current()
            if (msg == null) {
                // All messages written or buffer is empty for now.
                // The flush operation from pipeline signals when to stop trying to pull from buffer.
                break
            }

            if (msg is ByteBuf) {
                val buf = msg
                val readableBytes = buf.readableBytes()

                if (readableBytes == 0) {
                    // Remove the empty buffer and succeed its promise.
                    // The promise is retrieved internally by buffer.remove() and succeeded.
                    buffer.remove()
                    continue
                }

                try {
                    var writtenBytes = 0
                    while (writtenBytes < readableBytes) {
                        // writeBytes returns bytes *read from source*, i.e., written to dest
                        // It advances the readerIndex of the source ByteBuf (buf)
                        val localWritten = buf.readBytes(nioChannel, readableBytes - writtenBytes)

                        if (localWritten <= 0) { // Should only happen if readableBytes was 0 initially or non-blocking IO has 0 to write
                            if (buf.readableBytes() == readableBytes) { // Nothing was written at all
                                // If non-blocking and wrote 0, break to allow event loop to do other things.
                                // Netty's selector will notify when writable again for NioSocketChannel.
                                // For FileChannel, writes are typically blocking unless configured otherwise,
                                // or if it's an AsynchronousFileChannel (which we are not directly using here in doWrite).
                                // If nioChannel is a blocking FileChannel, localWritten > 0 or exception.
                                // If it *could* be non-blocking and return 0, we'd break here.
                                logger.warn("No bytes written for ByteBuf, assuming non-blocking and needs retry or buffer full downstream.")
                            }
                            break // Break inner loop, will re-evaluate outer for partial write
                        }
                        writtenBytes += localWritten
                        // Progress is implicitly handled by ByteBuf.readBytes advancing readerIndex
                    }

                    if (buf.readableBytes() == 0) { // Entire ByteBuf was written
                        // Remove the message from the buffer. This also retrieves and SUCCEEDS its associated promise.
                        buffer.remove()
                    } else {
                        // Partial write: The message (ByteBuf) remains at the head of the ChannelOutboundBuffer.
                        // Its readerIndex is advanced by the amount written.
                        // Netty will call flush() again later, which will trigger doWrite() again,
                        // and we'll attempt to write the remaining bytes of this ByteBuf.
                        // No need to call buffer.progress() as the ByteBuf's readerIndex tracks progress.
                        break // Exit the while(true) loop; wait for next flush()
                    }

                } catch (e: IOException) {
                    // On exception, remove the current message and FAIL its promise with the caught exception.
                    buffer.remove(e)
                    pipeline().fireExceptionCaught(e) // Also notify pipeline
                    close(voidPromise()) // Close on write error
                    return
                }
            } else {
                // Unsupported message type
                // Remove the message and FAIL its promise.
                buffer.remove(UnsupportedOperationException("Unsupported message type: ${msg.javaClass.name}"))
            }
        }
    }

    // --- Helper methods for half-closure (like Sockets) ---
    fun shutdownInput(): ChannelFuture {
        val promise = newPromise()
        if (eventLoop().inEventLoop()) {
            shutdownInput0(promise)
        } else {
            eventLoop().execute { shutdownInput0(promise) }
        }
        return promise
    }

    private fun shutdownInput0(promise: ChannelPromise) {
        if (!inputShutdown) {
            inputShutdown = true
            try {
                nioInputStreamChannel?.close() // Close the input side
                logger.debug("{} input stream shutdown", this)
                promise.setSuccess()
            } catch (e: IOException) {
                logger.warn("Failed to shutdown input stream for {}", this, e)
                promise.setFailure(e)
            }
        } else {
            promise.setSuccess() // Already shutdown
        }
    }

    fun shutdownOutput(): ChannelFuture {
        val promise = newPromise()
        if (eventLoop().inEventLoop()) {
            shutdownOutput0(promise)
        } else {
            eventLoop().execute { shutdownOutput0(promise) }
        }
        return promise
    }

    private fun shutdownOutput0(promise: ChannelPromise) {
        if (!outputShutdown) {
            outputShutdown = true
            try {
                nioOutputStreamChannel?.close() // Close the output side
                logger.debug("{} output stream shutdown", this)
                promise.setSuccess()
            } catch (e: IOException) {
                logger.warn("Failed to shutdown output stream for {}", this, e)
                promise.setFailure(e)
            }
        } else {
            promise.setSuccess() // Already shutdown
        }
    }

    fun isInputShutdown(): Boolean = inputShutdown
    fun isOutputShutdown(): Boolean = outputShutdown


    // --- Unsafe Implementation ---
    private inner class FildesChannelUnsafe : AbstractUnsafe() {
        override fun connect(
            remoteAddress: SocketAddress?, // Could be another FildesAddress if connecting two FDs
            localAddress: SocketAddress?,   // Should be this channel's FildesAddress
            promise: ChannelPromise
        ) {
            if (!promise.setUncancellable()) {
                close(voidPromise())
                return
            }

            if (isActive) { // Already "connected"
                promise.setSuccess()
                return
            }

            try {
                // The main "connection" logic: ensure our NIO channels are ready.
                // If they were initialized in FildesChannel's init, this is more of a state check.
                // If they were NOT, this is where you'd create FileInputStream(fd).channel etc.
                if (this@FildesChannel.nioInputStreamChannel == null && this@FildesChannel.nioOutputStreamChannel == null) {
                    // Attempt to initialize them now if not done in constructor
                    // This example assumes they are done in constructor or connect implies different meaning
                    try {
                        if (!fd.valid()) throw IOException("FileDescriptor is invalid for connect")
                        this@FildesChannel.nioInputStreamChannel = FileInputStream(fd).channel
                        this@FildesChannel.nioOutputStreamChannel = FileOutputStream(fd).channel
                    } catch (e: IOException) {
                        promise.setFailure(e)
                        close(voidPromise())
                        return
                    }
                }


                if (!isOpen) { // Check again after potential initialization
                    promise.setFailure(IOException("Channel resources not open after connect attempt"))
                    close(voidPromise())
                    return
                }

                // If remoteAddress is a FildesAddress, you might store it.
                // For now, this.remoteAddress will be set by AbstractChannel based on remoteAddress0()
                // or the parameter if the superclass connect does that.
                // this@FildesChannel.remoteAddress = remoteAddress; // If you want to store it manually


                val wasActive = this@FildesChannel.isActive // Backing field directly for comparison
                this@FildesChannel.channelActive = true // Mark as active
                promise.setSuccess()

                if (!wasActive && this@FildesChannel.isActive) { // Check if state actually changed to active
                    pipeline().fireChannelActive()
                }

            } catch (e: Throwable) {
                promise.setFailure(e)
                close(voidPromise())
            }
        }
        // localAddress() and remoteAddress() in Unsafe are typically just getters
        // that delegate to localAddress0() and remoteAddress0(). AbstractUnsafe does this.
    }
}

