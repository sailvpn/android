package com.illiad.troad.service.handler.ip

import com.illiad.troad.service.Utils.closeOnFlush
import com.illiad.troad.service.Utils.isRunning
import com.illiad.troad.service.Utils.vpnReaderExecutor
import com.illiad.troad.service.Utils.vpnReadFileChannel
import com.illiad.troad.service.Utils.isReaderTaskSubmitted
import com.illiad.troad.service.Utils.readCondition
import com.illiad.troad.service.Utils.readerLock
import com.illiad.troad.service.Utils.workAvailable
import com.illiad.troad.service.event.MoreBytes
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.io.IOException
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.withLock

@ChannelHandler.Sharable
object InputHandler : ChannelInboundHandlerAdapter() {

    // Constants for proactive reading behavior
    // Max expected packet size, 65575 is the maximum length for ipv6, ipv4 is 65535
    private const val MAX_IP_PACKET_SIZE = 65575

    // How many times to retry immediately if 0 bytes are read
    private const val MAX_CONSECUTIVE_ZERO_READ_ATTEMPTS = 5

    // How long to pause if still no data after several attempts
    private const val SHORT_NAP_DURATION_MS = 10L

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        super.handlerAdded(ctx)
        println("InputHandler: Handler added. Initializing VPN Reader Executor.")
        if (vpnReaderExecutor == null || vpnReaderExecutor!!.isShutdown) {
            vpnReaderExecutor = Executors.newSingleThreadExecutor { r ->
                val t = Thread(r, "vpn-reader-thread-lock")
                t.isDaemon = true
                t
            }
        }
        ensureReaderTaskIsRunning(ctx)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        println("InputStreamHandler: Channel is active. Starting VPN reader thread.")
        // triger the reading of a packet
        ctx.fireUserEventTriggered(MoreBytes())
    }

    private fun ensureReaderTaskIsRunning(ctx: ChannelHandlerContext) {
        if (isReaderTaskSubmitted.compareAndSet(false, true)) {
            vpnReaderExecutor!!.submit {
                persistentReaderLoopWithLock(ctx)
            }
        }
    }

    private fun persistentReaderLoopWithLock(ctx: ChannelHandlerContext) {
        println("VPN Reader Thread (Lock): Persistent loop started. Waiting for signals...")
        try {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                readerLock.withLock { // Acquires the lock
                    // Wait while no work is available and still running
                    while (!workAvailable && isRunning && !Thread.currentThread().isInterrupted) {
                        println("VPN Reader Thread (Lock): Awaiting signal...")
                        try {
                            readCondition.await() // Releases lock, waits, reacquires lock on wakeup
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt() // Restore interrupt status
                            println("VPN Reader Thread (Lock): Await interrupted.")
                            // Break from inner while, outer loop will check interrupt status
                            return@persistentReaderLoopWithLock // Exit method
                        }
                    }
                    // If we are here, either workAvailable is true, or !isRunning, or interrupted.
                    if (!isRunning || Thread.currentThread().isInterrupted) {
                        return@persistentReaderLoopWithLock // Exit method
                    }
                    // Reset workAvailable flag after waking up to process one unit of work
                    workAvailable = false
                } // Lock is released here

                // We've been signaled and conditions are met
                println("VPN Reader Thread (Lock): Awakened. Starting proactive read.")
                if (ctx.channel().isActive && vpnReadFileChannel.isOpen) {
                    try {
                        readFromVpnProactively(ctx, vpnReadFileChannel)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        println("VPN Reader Thread (Lock): Proactive read was interrupted.")
                        // Loop will check interrupt status and exit
                    } catch (ioe: IOException) {
                        System.err.println("VPN Reader Thread (Lock): IOException during proactive read: ${ioe.message}")
                        handleReadExceptionOnEventLoop(ctx, ioe)
                    } catch (e: Exception) {
                        System.err.println("VPN Reader Thread (Lock): Unexpected error during proactive read: ${e.message}")
                        handleReadExceptionOnEventLoop(ctx, e)
                    }
                } else {
                    println("VPN Reader Thread (Lock): Awakened, but channel not active or file not open.")
                }
            }
        } finally {
            println("VPN Reader Thread (Lock): Persistent loop finished.")
            isReaderTaskSubmitted.set(false)
        }
    }


    /**
     * Reads proactively from the VPN FileChannel.
     * This method is intended to run in a dedicated executor thread.
     * It will loop, attempting to read data, until:
     *  1. A packet (bytesRead > 0) is successfully read and dispatched to the Netty pipeline.
     *  2. EOF (bytesRead == -1) is detected on the FileChannel.
     *  3. The `isRunning` flag becomes false (indicating VpnService is stopping).
     *  4. The current thread is interrupted.
     *  5. An unrecoverable IOException occurs.
     *  6. The associated Netty channel (ctx.channel()) becomes inactive.
     *
     * @param ctx The ChannelHandlerContext for interacting with the Netty pipeline.
     * @param tunFileChannel The FileChannel to read VPN packets from.
     * @throws IOException if a critical I/O error occurs that prevents further reading.
     * @throws InterruptedException if the reading thread is interrupted.
     */
    private fun readFromVpnProactively(ctx: ChannelHandlerContext, tunFileChannel: FileChannel) {
        println("VPN Reader: Starting proactive read cycle.")
        val byteBuf = ctx.alloc().buffer(MAX_IP_PACKET_SIZE)
        var consecutiveZeroReadAttempts = 0

        try {
            // This outer loop ensures we keep trying as long as the VPN should be running
            // and we haven't successfully read a packet in *this specific invocation* of proactive read.
            while (isRunning && tunFileChannel.isOpen && !Thread.currentThread().isInterrupted) {
                // Check if the Netty channel itself is still active before attempting a read.
                // If not, there's no pipeline to send data to.
                if (!ctx.channel().isActive) {
                    println("VPN Reader: Netty channel (ctx) is no longer active. Stopping proactive read.")
                    return // Exit if the Netty channel is gone
                }

                byteBuf.clear() // Prepare buffer for a new read attempt
                val bytesRead: Int

                try {
                    // Attempt to read from the TUN device's FileChannel
                    bytesRead = byteBuf.writeBytes(tunFileChannel, byteBuf.writableBytes())
                } catch (ioe: IOException) {
                    // An IOException during the actual read operation
                    System.err.println("VPN Reader: IOException during tunFileChannel.read: ${ioe.message}")
                    throw ioe // Propagate to the main catch block of the submit task
                }

                if (bytesRead > 0) {
                    // --- Data Read Successfully ---
                    println("VPN Reader: Successfully read $bytesRead bytes from TUN.")

                    // Create a retained duplicate to pass to the Netty pipeline (different thread).
                    // The original byteBuf will be released in the finally block of this method.
                    val packetToForward = byteBuf.retainedDuplicate()

                    // Safely pass the read data to the Netty pipeline via the EventLoop.
                    if (ctx.channel().eventLoop().inEventLoop()) {
                        if (ctx.channel().isActive) { // Double check before firing
                            ctx.fireChannelRead(packetToForward)
                        } else {
                            packetToForward.release() // Release if channel became inactive just now
                            println("VPN Reader: Netty channel became inactive before fireChannelRead.")
                        }
                    } else {
                        ctx.channel().eventLoop().execute {
                            if (ctx.channel().isActive) {
                                ctx.fireChannelRead(packetToForward)
                            } else {
                                packetToForward.release()
                                println("VPN Reader: Netty channel became inactive before fireChannelRead (event loop).")
                            }
                        }
                    }
                    // A packet has been successfully read and dispatched.
                    // This "proactive read" session for the current MoreBytes event is complete.
                    return // EXIT the method.

                } else if (bytesRead == -1) {
                    // --- End Of File (EOF) ---
                    println("VPN Reader: EOF detected on TUN FileChannel.")
                    isRunning = false // Signal that the VPN source is finished.
                    // Schedule the channel close operation on the EventLoop.
                    if (ctx.channel().eventLoop().inEventLoop()) {
                        closeOnFlush(ctx.channel())
                    } else {
                        ctx.channel().eventLoop().execute { closeOnFlush(ctx.channel()) }
                    }
                    return // EOF, so exit the method.

                } else { // bytesRead == 0
                    // --- No Data Read in This Attempt ---
                    consecutiveZeroReadAttempts++
                    // println("VPN Reader: No data read (attempt $consecutiveZeroReadAttempts).")

                    if (consecutiveZeroReadAttempts >= MAX_CONSECUTIVE_ZERO_READ_ATTEMPTS) {
                        // After several immediate retries with no data, take a short nap
                        // to avoid busy-waiting if the TUN interface is genuinely idle.
                        // println("VPN Reader: Max zero-read attempts reached. Napping for ${SHORT_NAP_DURATION_MS}ms.")
                        try {
                            Thread.sleep(SHORT_NAP_DURATION_MS)
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt() // Restore interrupt status
                            println("VPN Reader: Sleep interrupted during zero-read backoff. Exiting proactive read.")
                            // Re-throw so the outer catch in the submit block handles it.
                            // This indicates an external request to stop.
                            throw ie
                        }
                        consecutiveZeroReadAttempts = 0 // Reset counter after napping
                    }
                    // Continue to the next iteration of the while loop to try reading again.
                }
            } // End of while loop
        } finally {
            // Always release the ByteBuf allocated at the beginning of this method.
            // If a packet was read, a retainedDuplicate was passed on, so this original is safe to release.
            // If no packet was read (e.g. loop exited due to !isRunning), it also needs release.
            if (byteBuf.refCnt() > 0) {
                byteBuf.release()
            }
            println("VPN Reader: Exiting proactive read cycle.")
        }

        // If the loop exits due to !isRunning, interruption, or TUN not open,
        // this point is reached without returning from inside the loop.
        if (!isRunning) {
            println("VPN Reader: isRunning became false. Proactive read is stopping.")
        }
        if (Thread.currentThread().isInterrupted) {
            println("VPN Reader: Thread was interrupted. Proactive read is stopping.")
            // Ensure the interrupt is propagated if this method is expected to throw it
            throw InterruptedException("VPN Reader thread interrupted during proactive read.")
        }
        if (!tunFileChannel.isOpen) {
            println("VPN Reader: TUN FileChannel is no longer open. Proactive read is stopping.")
            // Potentially signal an error or close the Netty channel if this is unexpected
            if (ctx.channel().isActive) { // Check before scheduling close
                if (ctx.channel().eventLoop().inEventLoop()) {
                    closeOnFlush(ctx.channel())
                } else {
                    ctx.channel().eventLoop().execute { closeOnFlush(ctx.channel()) }
                }
            }
        }
    }

    // Helper to ensure exception handling is on the EventLoop
    private fun handleReadException(ctx: ChannelHandlerContext, e: Throwable) {
        ctx.fireExceptionCaught(e)
        closeOnFlush(ctx.channel())
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
        super.userEventTriggered(ctx, evt)
        if (evt is MoreBytes) {
            println("InputHandler: MoreBytes event received.")
            triggerReadWithLock()
        }
    }

    private fun triggerReadWithLock() {
        if (!isRunning) {
            println("InputHandler: Not triggering read, isRunning is false.")
            return
        }
        readerLock.withLock {
            println("InputHandler: Signaling reader thread (Lock).")
            workAvailable = true
            readCondition.signal() // Wakes up one waiting thread (our reader thread)
        }
    }

    private fun handleReadExceptionOnEventLoop(ctx: ChannelHandlerContext, e: Throwable) {
        // Ensure ctx operations are on the event loop
        if (ctx.channel().eventLoop().inEventLoop()) {
            handleReadException(ctx, e) // Your existing handler
        } else {
            ctx.channel().eventLoop().execute { handleReadException(ctx, e) }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        println("InputHandler: Channel is inactive. Preparing to shutdown VPN reader.")
        shutdownReaderExecutor() // Call common shutdown logic
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) { // Good place for final cleanup
        super.handlerRemoved(ctx)
        println("InputHandler: Handler removed. Shutting down VPN reader.")
        shutdownReaderExecutor()
    }

    private fun shutdownReaderExecutor() {
        isRunning = false // Signal all loops to stop

        // Wake up the reader thread if it's waiting so it can terminate gracefully
        readerLock.withLock {
            workAvailable = false // Prevent further work even if signaled for shutdown
            readCondition.signalAll() // In case multiple threads (though we have one) or complex conditions
        }

        vpnReaderExecutor.let { executor ->
            if (executor!!.isShutdown) {
                println("InputHandler: Shutting down VPN Reader ExecutorService.")
                executor.shutdown() // Disable new tasks from being submitted
                try {
                    // Wait a while for existing tasks to terminate
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        println("InputHandler: VPN Reader ExecutorService did not terminate in time, forcing shutdown.")
                        executor.shutdownNow() // Cancel currently executing tasks
                        // Wait a while for tasks to respond to being cancelled
                        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                            System.err.println("InputHandler: VPN Reader ExecutorService did not terminate after shutdownNow.")
                        }
                    }
                } catch (_: InterruptedException) {
                    println("InputHandler: Interrupted while waiting for executor shutdown.")
                    executor.shutdownNow() // Re-cancel if current thread was interrupted
                    Thread.currentThread().interrupt() // Preserve interrupt status
                }
            }
        }
        vpnReaderExecutor = null // Allow GC
        isReaderTaskSubmitted.set(false) // Reset for potential re-addition
        println("InputHandler: VPN Reader Executor shutdown complete.")
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, throwable: Throwable?) {
        ctx.fireExceptionCaught(throwable)
        closeOnFlush(ctx.channel())
    }

}