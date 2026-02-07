package com.illiad.troad.model

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.illiad.troad.TroadApplication

class SettingsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            // Cast to your app class to get the singleton instance
            val troadStore = (application as TroadApplication).troadStore
            return SettingsViewModel(application, troadStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
