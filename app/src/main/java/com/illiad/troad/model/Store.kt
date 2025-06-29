package com.illiad.troad.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// At the top level of your Kotlin file (e.g., outside a class)
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "troad_app_store")

object StoreKeys {
    val SERVER_DOMAIN = stringPreferencesKey("server_domain")
    val SERVER_PORT = intPreferencesKey("server_port")
    val SHARED_SECRET = stringPreferencesKey("shared_secret")
    val TUN_IP = stringPreferencesKey("tun_ip")
}
@Singleton
class TroadStore @Inject constructor(@ApplicationContext private val context: Context) {

    init { }

    val serverDomainFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[StoreKeys.SERVER_DOMAIN] ?: ""
        }

    suspend fun saveServerDomain(domain: String) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.SERVER_DOMAIN] = domain
        }
    }

    val serverPortFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[StoreKeys.SERVER_PORT] ?: 1080
        }

    suspend fun saveServerPort(port: Int) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.SERVER_PORT] = port
        }
    }

    val sharedSecretFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[StoreKeys.SHARED_SECRET] ?: "password"
        }

    suspend fun saveSharedSecret(secret: String) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.SHARED_SECRET] = secret
        }
    }

    val tunIpFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[StoreKeys.TUN_IP] ?: "10.0.2.2"
        }

    suspend fun saveTunIp(ip: String) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.TUN_IP] = ip
        }
    }

    // ... similar flows and save functions for serverPort, sharedSecret ...
}

// In your ViewModel:
// class ProxySettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
//     val serverDomain: StateFlow<String> = settingsRepository.serverDomainFlow
//         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
//
//     fun onDomainChange(newDomain: String) {
//         viewModelScope.launch {
//             settingsRepository.saveServerDomain(newDomain)
//         }
//         // ...
//     }
//     // ...
// }