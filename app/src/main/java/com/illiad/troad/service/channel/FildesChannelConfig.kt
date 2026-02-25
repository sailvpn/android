package com.illiad.troad.service.channel

import io.netty.channel.DefaultChannelConfig
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.AdaptiveRecvByteBufAllocator
import io.netty.channel.ChannelOption

class FildesChannelConfig(channel: FildesChannel) : DefaultChannelConfig(channel) {

    init {
        // Sensible defaults for a file-like channel
        setAllocator(ByteBufAllocator.DEFAULT) // Or a specific one if needed
        setRecvByteBufAllocator(AdaptiveRecvByteBufAllocator.DEFAULT)
        setAutoRead(true) // auto-read by default
        // Other options can be set here or exposed via setters
    }

    // You can add FildesChannel-specific options here if needed
    // For example:
    // var fileReadMode: ReadMode = ReadMode.SEQUENTIAL
    // enum class ReadMode { SEQUENTIAL, RANDOM_ACCESS }

    override fun getOptions(): Map<ChannelOption<*>, Any> {
        return super.getOptions().toMutableMap() // Add your custom options if any
    }

    override fun <T : Any?> getOption(option: ChannelOption<T>?): T? {
        // Handle your custom options
        return super.getOption(option)
    }

    override fun <T : Any?> setOption(option: ChannelOption<T>?, value: T): Boolean {
        // Handle your custom options
        return super.setOption(option, value)
    }
}
