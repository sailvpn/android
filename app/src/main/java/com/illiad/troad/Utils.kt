package com.illiad.troad

import android.content.res.Resources
import android.os.ParcelFileDescriptor
import com.illiad.troad.service.channel.FildesChannel
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import kotlinx.coroutines.flow.MutableStateFlow

object Utils {

    var settings: Settings? = null

    val settingState = MutableStateFlow(settings);

    /** The [android.os.ParcelFileDescriptor] for the VPN tunnel interface provided by the Android system. Null if the VPN is not prepared or has been shut down. */
    var vpnInterface: ParcelFileDescriptor? = null

    /** The custom Netty [com.illiad.troad.service.channel.FildesChannel] used for reading from and writing to the VPN file descriptor. */
    var fildesChannel: FildesChannel? = null

    // resources getters
    private val rss: Resources = Resources.getSystem()

    fun getString(id: Int): String {
        return rss.getString(id)
    }

    fun getInt(id: Int): Int {
        return rss.getInteger(id)
    }



    /**
     * Closes the specified channel after all queued write requests are flushed.
     */

    fun closeOnFlush(ch: Channel) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }
}