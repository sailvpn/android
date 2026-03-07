package com.illiad.troad

import android.content.res.Resources
import com.illiad.troad.service.Settings

object Utils {
    var settings: Settings? = null
    var header: String? = null

    // resources getters
    private val rss: Resources = Resources.getSystem()

    fun getString(id: Int): String {
        return rss.getString(id)
    }

    fun getInt(id: Int): Int {
        return rss.getInteger(id)
    }

}