package com.illiad.troad.model

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ProxySettingsViewModelFactory(
    private val application: Application // To pass to AndroidViewModel and to create TroadStore
) : ViewModelProvider.Factory { // Use ViewModelProvider.Factory (or AndroidViewModelFactory if you want to reuse its app logic)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProxySettingsViewModel::class.java)) {
            // 1. Create TroadStore instance using the application context
            val troadStore = TroadStore(application.applicationContext)

            // 2. Create ProxySettingsViewModel with Application and TroadStore
            return ProxySettingsViewModel(application, troadStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}