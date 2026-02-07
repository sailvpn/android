package com.illiad.troad

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.Log
import com.illiad.troad.service.security.Dtls
import com.illiad.troad.service.security.Ssl
import com.illiad.troad.model.TroadStore
class TroadApplication : Application(), ComponentCallbacks2 {

    // Lazy ensures it's only created when first needed
    val troadStore: TroadStore by lazy {
        TroadStore(applicationContext)
    }
    override fun onCreate() {
        super.onCreate()
        // Your app initialization
        Ssl.initialize(this)
        Dtls.initialize(this)

    }

    /**
     * Release memory when the system informs us that it is low.
     * @param level the urgency of the memory request.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level) // It's good practice to call super

        Log.i("TroadApplication", "onTrimMemory() called with level: $level")

        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // System is running low on memory, aggressively release caches
            // MyImageCache.getInstance().clearMemory()
            Log.w("TroadApplication", "Aggressively trimming memory for RUNNING_LOW or worse.")
        }

        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // UI is no longer visible, release UI-specific resources
            // MyUiResourceHolder.clearResources()
            Log.i("TroadApplication", "Trimming memory because UI is hidden.")
        }

        if (level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE || level == ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            // App is a good candidate for killing if more memory is needed
            // Release almost everything that's not critical
            Log.w(
                "TroadApplication",
                "Trimming memory with COMPLETE or MODERATE. Releasing almost everything."
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // This is an older, more drastic callback.
        // onTrimMemory with TRIM_MEMORY_COMPLETE is roughly equivalent.
        Log.e("TroadApplication", "onLowMemory() called. This is serious!")
        // Release as many resources as possible.
    }
}