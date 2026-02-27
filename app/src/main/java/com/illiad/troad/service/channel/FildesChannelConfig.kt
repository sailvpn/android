package com.illiad.troad.service.channel

import io.netty.channel.DefaultChannelConfig
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.AdaptiveRecvByteBufAllocator
import io.netty.channel.ChannelOption
import io.netty.channel.*

class FildesChannelConfig(channel: FildesChannel) : DefaultChannelConfig(channel) {

    init {
        // Use a Pooled allocator if this is high-throughput file I/O
        setAllocator(ByteBufAllocator.DEFAULT)
        setRecvByteBufAllocator(AdaptiveRecvByteBufAllocator.DEFAULT)

        // Essential for flow control in proxy/tunnel scenarios
        setAutoRead(true)

        // Set default watermarks (e.g., 32KB low, 64KB high)
        setWriteBufferWaterMark(WriteBufferWaterMark(32 * 1024, 64 * 1024))
    }

    override fun getOptions(): Map<ChannelOption<*>, Any> {
        return getOptions(
            super.getOptions(),
            ChannelOption.AUTO_READ,
            ChannelOption.WRITE_BUFFER_WATER_MARK
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getOption(option: ChannelOption<T>): T? {
        return when (option) {
            // Add custom options here if you add specific file modes
            else -> super.getOption(option)
        }
    }

    override fun <T : Any?> setOption(option: ChannelOption<T>, value: T): Boolean {
        validate(option, value)

        return when (option) {
            // Example of intercepting a standard option to trigger internal logic
            ChannelOption.AUTO_READ -> {
                val result = super.setOption(option, value)
                if (value as Boolean) {
                    channel.read() // Trigger a read if auto-read is toggled on
                }
                result
            }

            else -> super.setOption(option, value)
        }
    }
}

