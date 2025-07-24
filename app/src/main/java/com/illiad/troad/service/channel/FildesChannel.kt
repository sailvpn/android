package com.illiad.troad.service.channel

import FildesAddress
import io.netty.buffer.ByteBuf
import io.netty.channel.AbstractChannel
import io.netty.channel.Channel
import io.netty.channel.ChannelConfig
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelMetadata
import io.netty.channel.ChannelOutboundBuffer
import io.netty.channel.ChannelPromise
import io.netty.channel.EventLoop
import io.netty.util.internal.logging.InternalLoggerFactory
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketAddress
import java.util.concurrent.TimeUnit
import java.nio.channels.FileChannel as NioFileChannel // Alias to avoid clash

class FildesChannel(parent: Channel?, private val fd: FileDescriptor) : AbstractChannel(parent) {

    private val logger = InternalLoggerFactory.getInstance(FildesChannel::class.java)
    private val config = FildesChannelConfig(this) // Assuming FildesChannelConfig exists

    private var nioInputStreamChannel: NioFileChannel? = null
    private var nioOutputStreamChannel: NioFileChannel? = null

    @Volatile
    private var inputShutdown = false // Flag to indicate if input is shut down

    @Volatile
    private var outputShutdown = false // Flag for output shutdown (symmetric to input)

    @Volatile
    private var channelActive = false

    @Volatile // Ensure visibility across threads, though operations should be on eventloop
    private var isPaused = false
    private val ZERO_READ_PAUSE_MS = 10L // 10 milliseconds

    private val fildesAddress = FildesAddress(fd) // Assuming FildesAddress exists

    init {
        // Initialization of nioInputStreamChannel and nioOutputStreamChannel
        try {
            if (!fd.valid()) {
                throw IOException("FileDescriptor is invalid")
            }
            // assuming R/W FD or appropriate error handling elsewhere.
            this.nioInputStreamChannel = FileInputStream(fd).channel
            this.nioOutputStreamChannel = FileOutputStream(fd).channel
        } catch (e: IOException) {
            logger.warn("Failed to initialize NIO FileChannels from FD", e)
            this.nioInputStreamChannel = null
            this.nioOutputStreamChannel = null
            // Consider if the channel should be immediately closed or marked as failed here.
        }
    }

    // ... other AbstractChannel method overrides (newUnsafe, config, metadata, etc.) ...
    override fun newUnsafe(): AbstractUnsafe = FildesChannelUnsafe() // Defined below or separately
    override fun config(): ChannelConfig = config
    override fun metadata(): ChannelMetadata = ChannelMetadata(false)
    override fun isOpen() =
        fd.valid() && (nioInputStreamChannel?.isOpen == true || nioOutputStreamChannel?.isOpen == true)

    override fun isActive() = isOpen && channelActive
    override fun isCompatible(loop: EventLoop?) = true
    override fun localAddress0(): SocketAddress = fildesAddress

    // Typically null for FD-based channel unless connecting to another FD
    override fun remoteAddress0(): SocketAddress? = null

    override fun doBind(localAddress: SocketAddress?) { /* ... see previous examples ... */
        if (localAddress !is FildesAddress || localAddress.fd != this.fd) {
            throw IllegalArgumentException("localAddress must be the FildesAddress of this channel")
        }
        if (!fd.valid()) {
            throw IOException("Cannot bind: FileDescriptor is invalid")
        }
        // If nio channels were not initialized in init, do it here.
        // For this example, let's assume init did it or connect() does it.
        logger.debug("{} bound to {}", this, localAddress)
        // If binding implies the channel is immediately ready for I/O after this call
        // and before connect(), you might set channelActive = true here and fireChannelActive.
        // However, typically for client-style channels, active state is after connect().
    }

    override fun doDisconnect() {
        doClose()
    }

    override fun doClose() {
        logger.debug("{} closing", this)
        channelActive = false
        inputShutdown = true // Mark as shutdown
        outputShutdown = true // Mark as shutdown

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
        // As before: DO NOT close 'fd' itself here unless FildesChannel OWNS it.
        logger.info("{} closed", this)
    }

    override fun doBeginRead() {

        if (isPaused) {
            // We are waiting for the scheduled resume.
            // The scheduled task will re-trigger doBeginRead.
            logger.trace(
                "{} in paused state.",
                this
            )
            return
        }

        if (inputShutdown || !isActive) {
            return
        }

        val allocHandle = unsafe().recvBufAllocHandle()
        // assume your allocHandle from unsafe() is correctly reset or configured.
        // let's explicitly reset messagesRead for this read attempt cycle.
        // Essential if not done by unsafe().recvBufAllocHandle() for each "read cycle"
        allocHandle.reset(config())

        var continueReading = false
        do {
            val byteBuf = allocHandle.allocate(config().allocator)
            var bytesRead: Int

            try {
                bytesRead =
                    byteBuf.writeBytes(nioInputStreamChannel, byteBuf.writableBytes())

                if (bytesRead > 0) {
                    allocHandle.lastBytesRead(bytesRead)
                    allocHandle.incMessagesRead(1)
                    pipeline().fireChannelRead(byteBuf)
                    // Continue reading based on allocator's decision
                    continueReading = allocHandle.continueReading() && config().isAutoRead
                } else if (bytesRead == 0) {
                    // bytesRead == 0 (and buffer is still writable)
                    // THIS IS THE CRITICAL 0-BYTE READ SCENARIO
                    byteBuf.release() // Release the allocated buffer that wasn't used

                    logger.trace(
                        "{} read 0 bytes. Pause for {} ms.",
                        this,
                        ZERO_READ_PAUSE_MS
                    )
                    // pause the read loop
                    isPaused = true

                    // Schedule a task to clear the flag and re-trigger a read.
                    // This task will run on the EventLoop, so it's non-blocking.
                    eventLoop().schedule({
                        // resume reading
                        isPaused = false
                        logger.trace(
                            "{} pause ended.",
                            this
                        )
                        // restart the read loop
                        pipeline().fireChannelReadComplete()
                    }, ZERO_READ_PAUSE_MS, TimeUnit.MILLISECONDS)

                    break
                } else if (bytesRead < 0) { // EOF
                    allocHandle.lastBytesRead(-1) // Signal EOF to allocator
                    byteBuf.release()
                    shutdownInput() // Initiate input shutdown
                    break
                }
            } catch (e: IOException) {
                byteBuf.release()
                pipeline().fireExceptionCaught(e)
                // Shutdown logic as before
                shutdownInput().addListener { future ->
                    if (!future.isSuccess) {
                        logger.warn(
                            "Error during shutdownInput after read exception",
                            future.cause()
                        )
                    }
                }
                // Stop on error
                return // Exit doBeginRead after error
            }

        } while (continueReading) // ContinueReading will be false if we paused or other conditions met

        // This is called AFTER the loop, whether we read data, hit EOF, or initiated a pause.
        // If autoRead is true, HeadContext might call read() again, leading back to doBeginRead().
        pipeline().fireChannelReadComplete()
    }


    override fun doWrite(buffer: ChannelOutboundBuffer) {
        if (outputShutdown || !isActive) {
            // Drain the buffer and fail promises if not active or output shutdown
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


    // --- Input Shutdown Implementation ---

    /**
     * Shuts down the input side of this FildesChannel.
     * No more data can be read from the underlying FileDescriptor through this channel.
     * This operation is idempotent.
     *
     * @return a [ChannelFuture] that will be notified when the input shutdown is complete.
     */
    fun shutdownInput(): ChannelFuture {
        val promise =
            newPromise() // Creates a promise associated with this channel and its event loop
        if (eventLoop().inEventLoop()) {
            shutdownInput0(promise)
        } else {
            eventLoop().execute { // Ensure execution within the event loop for thread safety
                shutdownInput0(promise)
            }
        }
        return promise
    }

    /**
     * Internal method to perform the input shutdown logic.
     * Must be called from within the EventLoop.
     */
    private fun shutdownInput0(promise: ChannelPromise) {
        if (!promise.setUncancellable()) { // Mark the promise as uncancellable
            return // Promise was already completed (e.g., cancelled)
        }

        if (inputShutdown) {
            // Already shut down, so immediately succeed the promise.
            promise.setSuccess()
            return
        }

        inputShutdown = true // Set the flag to prevent further reads and multiple shutdowns
        logger.debug("{} shutting down input", this)

        val inputChannel = this.nioInputStreamChannel
        if (inputChannel == null || !inputChannel.isOpen) {
            // If the NIO channel is already null or closed, consider the input effectively shutdown.
            promise.setSuccess()
            pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE) // Notify pipeline
            return
        }

        try {
            inputChannel.close() // Close the underlying NIO input channel
            logger.info("{} input stream channel closed successfully.", this)
            promise.setSuccess()
        } catch (e: IOException) {
            logger.warn("Failed to close NIO input stream channel for {}: {}", this, e.message, e)
            promise.setFailure(e) // Fail the promise if closing the NIO channel fails
            // Even if closing the nio channel fails, inputShutdown flag remains true.
            // Further reads will be blocked.
        } finally {
            // After attempting to close, regardless of success or failure,
            // fire an event to notify the pipeline that input shutdown has been attempted/completed.
            // This allows handlers to react to the input side being closed.
            if (promise.isSuccess) { // Only fire if the operation (flag setting + close attempt) considered successful
                pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE)
            }
        }
    }

    /**
     * Checks if the input side of this channel has been shut down.
     * @return `true` if input has been shut down, `false` otherwise.
     */
    fun isInputShutdown(): Boolean = inputShutdown

    // --- (Optional but good practice) Output Shutdown Implementation (Symmetric to Input) ---

    /**
     * Shuts down the output side of this FildesChannel.
     * No more data can be written to the underlying FileDescriptor through this channel.
     * This operation is idempotent.
     *
     * @return a [ChannelFuture] that will be notified when the output shutdown is complete.
     */
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
        if (!promise.setUncancellable()) return

        if (outputShutdown) {
            promise.setSuccess()
            return
        }
        outputShutdown = true
        logger.debug("{} shutting down output", this)

        val outputChannel = this.nioOutputStreamChannel
        if (outputChannel == null || !outputChannel.isOpen) {
            promise.setSuccess()
            pipeline().fireUserEventTriggered(ChannelOutputShutdownEvent.INSTANCE) // Notify pipeline
            return
        }

        try {
            outputChannel.close()
            logger.info("{} output stream channel closed successfully.", this)
            promise.setSuccess()
        } catch (e: IOException) {
            logger.warn("Failed to close NIO output stream channel for {}: {}", this, e.message, e)
            promise.setFailure(e)
        } finally {
            if (promise.isSuccess) {
                pipeline().fireUserEventTriggered(ChannelOutputShutdownEvent.INSTANCE)
            }
        }
    }

    fun isOutputShutdown(): Boolean = outputShutdown


    // --- Unsafe implementation (Inner class) ---
    private inner class FildesChannelUnsafe : AbstractUnsafe() {
        // Must be connect-oriented if it has an "active" state post-connection
        override fun connect(
            remoteAddress: SocketAddress?,
            localAddress: SocketAddress?,
            promise: ChannelPromise
        ) {
            if (!promise.setUncancellable()) {
                close(voidPromise())
                return
            }

            // For FildesChannel, "connect" could mean ensuring the FD is valid and NIO channels are open
            // and then marking the channel active.
            if (isActive) {
                promise.trySuccess() // Already active
                return
            }

            try {
                // Ensure local address is what we expect (our fildesAddress)
                if (localAddress != null && localAddress != this@FildesChannel.fildesAddress) {
                    throw IllegalArgumentException("localAddress mismatch on connect: $localAddress, expected ${this@FildesChannel.fildesAddress}")
                }
                // If localAddress is null, this.localAddress (fildesAddress) is implicitly used by AbstractChannel.

                // If nio channels weren't opened in init, this would be the place.
                // For this example, assume they are opened in init.
                if (nioInputStreamChannel == null && nioOutputStreamChannel == null) {
                    // This case indicates a problem during init, or they should be opened here.
                    // Let's re-attempt initialization here if they are null,
                    // as a more robust "connect" operation.
                    try {
                        if (!fd.valid()) throw IOException("FileDescriptor is invalid for connect")
                        if (this@FildesChannel.nioInputStreamChannel == null) {
                            this@FildesChannel.nioInputStreamChannel = FileInputStream(fd).channel
                        }
                        if (this@FildesChannel.nioOutputStreamChannel == null) {
                            this@FildesChannel.nioOutputStreamChannel = FileOutputStream(fd).channel
                        }
                    } catch (e: IOException) {
                        promise.setFailure(e)
                        close(voidPromise()) // Close channel if connect fails to set up resources
                        return
                    }
                }

                if (!isOpen) { // Check isOpen state AFTER attempting to ensure NIO channels are open
                    promise.setFailure(IOException("Channel is not open after connect attempt"))
                    close(voidPromise())
                    return
                }

                val wasActive = this@FildesChannel.isActive // Backing field from AbstractChannel
                this@FildesChannel.channelActive = true     // Set our active flag

                // The actual remoteAddress field in AbstractChannel will be set by AbstractUnsafe
                // using the remoteAddress parameter of connect() IF it's not null.
                // If remoteAddress parameter is null, it remains null.
                // Our remoteAddress0() returns null anyway for FildesChannel.

                promise.setSuccess()

                if (!wasActive && isActive) { // Check if the state actually changed to active
                    pipeline().fireChannelActive()
                }

            } catch (t: Throwable) {
                promise.setFailure(t)
                close(voidPromise()) // Close the channel on any connection error
            }
        }
    }
}

// Standard Netty events for half-closure, if you want to use them.
// These are not part of the core Netty API directly but are common patterns.
// You would define these simple marker objects.
object ChannelInputShutdownEvent {
    val INSTANCE = this
}

object ChannelOutputShutdownEvent {
    val INSTANCE = this
}


