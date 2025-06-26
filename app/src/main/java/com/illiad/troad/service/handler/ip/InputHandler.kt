package com.illiad.troad.service.handler.ip

import com.illiad.troad.service.Utils.closeOnFlush
import com.illiad.troad.service.Utils.vpnReadFileChannel
import com.illiad.troad.service.Utils.isRunning
import com.illiad.troad.service.Utils.vpnReaderExecutor
import com.illiad.troad.service.event.MoreBytes
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.io.IOException
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

@ChannelHandler.Sharable
object InputHandler : ChannelInboundHandlerAdapter() {

    // Constants for proactive reading behavior
    // Max expected packet size, 65575 is the maximum lengh for ipv6, ipv4 is 65535
    private const val MAX_IP_PACKET_SIZE = 65575
    // How many times to retry immediately if 0 bytes are read
    private const val MAX_CONSECUTIVE_ZERO_READ_ATTEMPTS = 5
    // How long to pause if still no data after several attempts
    private const val SHORT_NAP_DURATION_MS = 10L

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        println("InputStreamHandler: Channel is active. Starting VPN reader thread.")
        // Executor is created here, but the actual reading starts in response to MoreBytes or initial active
        // This is good for control.
        setupAndSubmit(ctx)
    }

    private fun setupAndSubmit(ctx: ChannelHandlerContext) {
        // Potentially re-creating executor on every MoreBytes might be slightly heavy.
        // Consider creating it once if the thread can be paused/resumed or if
        // the executor can manage a single persistent thread that waits on a signal.
        // However, for single submission and then it dies, this is fine.
        vpnReaderExecutor = Executors.newSingleThreadExecutor { r ->
            val t = Thread(r, "vpn-reader-thread")
            t.isDaemon = true
            t
        }
        isRunning = true // Should ideally be managed more tightly with the actual read loop status

        vpnReaderExecutor.submit {
            try {
                readFromVpnProactively(ctx, vpnReadFileChannel)
            } catch (e: Exception) {
                System.err.println("Error during VPN read submission/execution: ${e.message}")
                isRunning = false // Good to set this on error
                // Ensure ctx operations are on the event loop if this thread isn't it
                if (ctx.channel().eventLoop().inEventLoop()) {
                    handleReadException(ctx, e)
                } else {
                    ctx.channel().eventLoop().execute { handleReadException(ctx, e) }
                }
            }
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
        super.userEventTriggered(ctx, evt) // Good practice to call super
        if (evt is MoreBytes) {
            println("InputHandler: MoreBytes event received. Triggering next read.")
            if (isRunning && vpnReadFileChannel.isOpen) { // Only submit if still valid to run
                setupAndSubmit(ctx)
            } else {
                println("InputHandler: MoreBytes received, but not in a state to read (isRunning=$isRunning, isOpen=${vpnReadFileChannel.isOpen}).")
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) { // Added for cleanup
        super.channelInactive(ctx)
        println("InputHandler: Channel is inactive. Shutting down VPN reader executor.")
        isRunning = false
        vpnReaderExecutor.shutdown()
        // You might want to await termination for a short period if necessary
        // try {
        //    if (!vpnReaderExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        //        vpnReaderExecutor.shutdownNow()
        //    }
        // } catch (ie: InterruptedException) {
        //    vpnReaderExecutor.shutdownNow()
        //    Thread.currentThread().interrupt()
        // }
        // Close vpnReadFileChannel if this handler is responsible for its lifecycle
        // try { vpnReadFileChannel.close() } catch (e: IOException) { /* log */ }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, throwable: Throwable?) {
        ctx.fireExceptionCaught(throwable)
        closeOnFlush(ctx.channel())
    }

}