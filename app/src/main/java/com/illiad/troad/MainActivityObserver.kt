package com.illiad.troad

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class MainActivityObserver(private val onStartup:() -> Unit, private val onCleanup: () -> Unit) : DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        println("Activity is Created")
    }

    override fun onStart(owner: LifecycleOwner) {
        onStartup();
        println("Activity is visible")
    }

    override fun onResume(owner: LifecycleOwner) {
        println("Activity is in the foreground")
    }

    override fun onPause(owner: LifecycleOwner) {
        println("Activity is losing focus")
    }

    override fun onStop(owner: LifecycleOwner) {
        onCleanup() // Trigger your cleanup logic
        println("Activity is no longer visible")
    }

    override fun onDestroy(owner: LifecycleOwner) {
        println("Activity is being destroyed")
    }


}
