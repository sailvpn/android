package com.illiad.troad.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.illiad.troad.service.security.Cryptos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// At the top level of your Kotlin file (e.g., outside a class)
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "troad_app_store")

object StoreKeys {
    val SERVER_DOMAIN = stringPreferencesKey("server_domain")
    val SERVER_PORT = intPreferencesKey("server_port")
    val SELECTED_CRYPTO = stringPreferencesKey("selected_crypto")
    val SHARED_SECRET = stringPreferencesKey("shared_secret")
    val TUN_IP = stringPreferencesKey("tun_ip")
    val CA_CERT = stringPreferencesKey("ca_cert")
    val AUTO_RENEWAL = booleanPreferencesKey("auto_renewal")
    val JWT_TOKEN = stringPreferencesKey("jwt_token")
    val USERNAME = stringPreferencesKey("username")
    val PASSWORD = stringPreferencesKey("password")
    val DURATION = longPreferencesKey("duration")
    val INTERVAL = longPreferencesKey("interval")

}

class TroadStore(appContext: Context) {

    // This ensures we always use the long-lived application context
    private val context = appContext.applicationContext

    val serverDomainFlow: Flow<String> = context.dataStore.data
        .map { settings ->
            settings[StoreKeys.SERVER_DOMAIN] ?: ""
        }

    suspend fun saveServerDomain(domain: String) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.SERVER_DOMAIN] = domain
        }
    }

    val serverPortFlow: Flow<Int> = context.dataStore.data
        .map { settings ->
            settings[StoreKeys.SERVER_PORT] ?: 1080
        }

    suspend fun saveServerPort(port: Int) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.SERVER_PORT] = port
        }
    }

    val caCertFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[StoreKeys.CA_CERT] ?: "" }

    suspend fun saveCaCert(certContent: String) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.CA_CERT] = certContent
        }
    }

    val selectedCryptoFlow: Flow<Cryptos> = context.dataStore.data
        .map { settings ->
            val savedValue =
                settings[StoreKeys.SELECTED_CRYPTO] ?: Cryptos.JWT2.value // Default to JWT2
            Cryptos.fromValue(savedValue).orElse(Cryptos.JWT2)
        }

    suspend fun saveCryptoSelection(crypto: Cryptos) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.SELECTED_CRYPTO] = crypto.value
        }
    }

    val autoRenewalFlow: Flow<Boolean> = context.dataStore.data
        .map { settings ->
            settings[StoreKeys.AUTO_RENEWAL] ?: false
        }

    suspend fun saveAutoRenewal(checked: Boolean) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.AUTO_RENEWAL] = checked
        }
    }

    val sharedSecretFlow: Flow<String> = context.dataStore.data
        .map { settings ->
            settings[StoreKeys.SHARED_SECRET] ?: "password"
        }

    suspend fun saveSharedSecret(secret: String) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.SHARED_SECRET] = secret
        }
    }

    val jwtFlow: Flow<String> = context.dataStore.data
        .map { settings ->
            settings[StoreKeys.JWT_TOKEN] ?: ""
        }

    suspend fun saveJwt(token: String) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.JWT_TOKEN] = token
        }
    }

    val usernameFlow: Flow<String> = context.dataStore.data
        .map { settings ->
            settings[StoreKeys.USERNAME] ?: ""
        }

    suspend fun saveUsername(userName: String) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.USERNAME] = userName
        }

    }

    val passwordFlow: Flow<String> = context.dataStore.data
        .map { settings ->
            settings[StoreKeys.PASSWORD] ?: ""
        }

    suspend fun savePassword(password: String) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.PASSWORD] = password
        }
    }

    val durationFlow: Flow<Long> = context.dataStore.data
        .map { settings ->
            settings[StoreKeys.DURATION] ?: 60L
        }

    suspend fun saveDuration(duration: Long) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.DURATION] = duration
        }
    }

    val intervalFlow: Flow<Long> = context.dataStore.data
        .map { settings ->
            settings[StoreKeys.INTERVAL] ?: 5L
        }

    suspend fun saveInterval(interval: Long) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.INTERVAL] = interval
        }
    }

    val tunIpFlow: Flow<String> = context.dataStore.data
        .map { settings ->
            settings[StoreKeys.TUN_IP] ?: "10.0.2.2"
        }

    suspend fun saveTunIp(ip: String) {
        context.dataStore.edit { settings ->
            settings[StoreKeys.TUN_IP] = ip
        }
    }

    // ... similar flows and save functions for serverPort, sharedSecret ...
}