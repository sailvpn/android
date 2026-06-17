package com.illiad.troad.service

import com.illiad.troad.model.AutoRenew
import com.illiad.troad.model.Duration
import com.illiad.troad.model.TroadStore
import com.illiad.troad.service.security.Cryptos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

interface SettingsRepo {
    fun getSettingsStream(): Flow<Settings>
}

class SettingsRepoImp(private val tStore: TroadStore) : SettingsRepo {

    override fun getSettingsStream(): Flow<Settings> {
        val flows: List<Flow<Any?>> = listOf(
            tStore.serverDomainFlow,     // 0
            tStore.sniFlow,              // 1
            tStore.serverPortFlow,       // 2
            tStore.caCertFlow,           // 3
            tStore.selectedCryptoFlow,   // 4
            tStore.jwtFlow,              // 5 (Maps to activeToken via JWT/JWT2 rules if needed)
            tStore.autoRenewFlow,        // 6
            tStore.durationFlow,         // 7
            tStore.sharedSecretFlow      // 8
        )

        return combine(flows) { array ->
            Settings(
                domain = array[0] as? String ?: "",
                sni = array[1] as? String ?: "",
                port = array[2] as? Int ?: 443,
                cacert = array[3] as? String ?: "",
                crypto = array[4] as? Cryptos ?: Cryptos.JWT,
                jwt = array[5] as? String, // Cleanly mapped from index 5
                autoRenew = AutoRenew.fromMinutes(
                    array[6] as? Long ?: 0L
                ), // Cleanly mapped from index 6
                duration = array[7] as? Duration, // Cleanly mapped from index 7
                secret = array[8] as? String // Cleanly mapped from index 8 (No more index 10 crash!)
            )
        }
            .distinctUntilChanged()
    }
}

