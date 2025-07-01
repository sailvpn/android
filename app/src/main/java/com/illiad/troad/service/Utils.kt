package com.illiad.troad.service

import android.os.ParcelFileDescriptor
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

object Utils {

    // varibles for InputHandler
    @Volatile
    var vpnInterface: ParcelFileDescriptor? = null

    @Volatile
    lateinit var vpnReadFileChannel: FileChannel

    @Volatile
    lateinit var vpnWriteFileChannel: FileChannel

    @Volatile
    var isRunning: Boolean = false

    @Volatile
    var vpnReaderExecutor: ExecutorService? = null

    // Lock and Condition for signaling the reader thread
    val readerLock = ReentrantLock()
    val readCondition: Condition = readerLock.newCondition()

    @Volatile
    var workAvailable = false // Guard for spurious wakeups and initial start

    val MTU = 65575

    var serverDomain: String = "127.0.0.1"
    var serverPort: Int = 2080
    var sharedSecret: String = "sharedSecret"

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */

    fun closeOnFlush(ch: Channel) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }
}