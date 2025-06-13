package com.illiad.troad.service.handler

import io.netty.channel.ChannelHandlerContext
import com.illiad.troad.service.Utils
import com.illiad.troad.service.Utils.closeOnFlush
import io.netty.channel.ChannelInboundHandlerAdapter
import java.io.IOException
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

class InputStreamHandler : ChannelInboundHandlerAdapter() {

    private val vpnReaderExecutor = Executors.newSingleThreadExecutor { r ->
        val t = Thread(r, "vpn-reader-thread")
        t.isDaemon = true // So it doesn't prevent JVM shutdown
        t
    }
    private var fileChannel: FileChannel? = null


    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        println("InputStreamHandler: Channel is active. Starting VPN reader thread.")

        try {
            // Assuming Utils.vpnReadStream is a FileInputStream from VpnService's ParcelFileDescriptor
            fileChannel = Utils.vpnReadStream.channel // Get the FileChannel

            if (fileChannel == null || !fileChannel!!.isOpen) {
                val errorMsg = "VPN FileChannel is not available or not open."
                ctx.fireExceptionCaught(IOException(errorMsg))
                closeOnFlush(ctx.channel())
                return
            }

            // Start reading in a separate thread
            vpnReaderExecutor.submit {
                readFromVpnAndFireChannelRead(ctx, fileChannel!!)
            }

        } catch (e: Exception) {
            System.err.println("Error setting up VPN FileChannel: ${e.message}")
            ctx.fireExceptionCaught(e)
            closeOnFlush(ctx.channel())
        }
    }

    private fun readFromVpnAndFireChannelRead(ctx: ChannelHandlerContext, fc: FileChannel) {
        val byteBuf = ctx.alloc().buffer() // Allocate buffer once

        try {
            while (fc.isOpen && !Thread.currentThread().isInterrupted) {
                // byteBuf.clear() // Prepare buffer for new write from channel

                // writeBytes reads from fc into byteBuf. It's a blocking call.
                val bytesRead = byteBuf.writeBytes(fc, 2048) // Read up to 2KB, adjust as needed

                if (bytesRead > 0) {
                    println("VPN Reader Thread: Read $bytesRead bytes from VPN interface.")
                    // Data is now in byteBuf (writerIndex updated).
                    // fireChannelRead will pass it to the next handler.
                    // The next handler is responsible for releasing this byteBuf or propagating it.
                    ctx.fireChannelRead(byteBuf.retainedDuplicate()) // Pass a retained duplicate
                } else if (bytesRead == -1) {
                    println("VPN Reader Thread: End of stream reached on VPN interface.")
                    break // EOF
                } // else { // bytesRead == 0
                // This might happen if length to read was 0, or if channel is non-blocking
                // and no data (though FileChannel usually blocks).
                // Can add a small sleep here if 0 bytes are read continuously to avoid busy-wait,
                // }
                // sleep for a bit to avoid busy-waiting
                Thread.sleep(10)
            }
        } catch (e: Exception) {
            ctx.fireExceptionCaught(e)
            closeOnFlush(ctx.channel())
        } finally {
            closeOnFlush(ctx.channel())
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, throwable: Throwable?) {
        ctx.fireExceptionCaught(throwable)
        closeOnFlush(ctx.channel())
    }

}