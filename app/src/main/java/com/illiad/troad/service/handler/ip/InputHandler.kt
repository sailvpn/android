package com.illiad.troad.service.handler.ip

import com.illiad.troad.service.Utils.closeOnFlush
import com.illiad.troad.service.Utils.vpnReadFileChannel
import com.illiad.troad.service.event.MoreBytes
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.io.IOException
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

@ChannelHandler.Sharable
object InputHandler : ChannelInboundHandlerAdapter() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        println("InputStreamHandler: Channel is active. Starting VPN reader thread.")
        val vpnReaderExecutor = Executors.newSingleThreadExecutor { r ->
            val t = Thread(r, "vpn-reader-thread")
            t.isDaemon = true // So it doesn't prevent JVM shutdown
            t
        }
        try {
            if (!vpnReadFileChannel.isOpen) {
                val errorMsg = "VPN FileChannel is not available or not open."
                ctx.fireExceptionCaught(IOException(errorMsg))
                closeOnFlush(ctx.channel())
                return
            }

            // Start reading in a separate thread
            vpnReaderExecutor.submit {
                readFromVpn(ctx, vpnReadFileChannel)
            }

        } catch (e: Exception) {
            System.err.println("Error setting up VPN FileChannel: ${e.message}")
            ctx.fireExceptionCaught(e)
            closeOnFlush(ctx.channel())
        }
    }

    private fun readFromVpn(ctx: ChannelHandlerContext, fc: FileChannel) {

        val byteBuf = ctx.alloc().buffer() // Allocate buffer once
        var keepReading = true

        try {
            while (keepReading && vpnReadFileChannel.isOpen && !Thread.currentThread().isInterrupted) {

                // writeBytes reads from fc into byteBuf. It's a blocking call.
                val bytesRead =
                    byteBuf.writeBytes(fc, 65575) // ipv6 maximum length 65575, ipv4 65535,

                if (bytesRead == 0) {
                    // no data, sleep for a bit to avoid busy-waiting
                    Thread.sleep(10)
                } else if (bytesRead > 0) {
                    println("VPN Reader Thread: Read $bytesRead bytes from VPN interface.")
                    // Data is now in byteBuf (writerIndex updated).
                    // fireChannelRead will pass it to the next handler.
                    // The next handler is responsible for releasing this byteBuf or propagating it.
                    ctx.fireChannelRead(byteBuf.retainedDuplicate()) // Pass a retained duplicate
                    keepReading = false
                } else if (bytesRead == -1) {
                    println("VPN Reader Thread: End of stream reached on VPN interface.")
                    keepReading = false // EOF
                }
            }
        } catch (e: Exception) {
            ctx.fireExceptionCaught(e)
            closeOnFlush(ctx.channel())
        } finally {
            closeOnFlush(ctx.channel())
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        super.userEventTriggered(ctx, evt)
        if (evt is MoreBytes) {
            val vpnReaderExecutor = Executors.newSingleThreadExecutor { r ->
                val t = Thread(r, "vpn-reader-thread")
                t.isDaemon = true // So it doesn't prevent JVM shutdown
                t
            }
            // Start reading in a separate thread
            vpnReaderExecutor.submit {
                readFromVpn(ctx!!, vpnReadFileChannel)
            }

        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, throwable: Throwable?) {
        ctx.fireExceptionCaught(throwable)
        closeOnFlush(ctx.channel())
    }

}