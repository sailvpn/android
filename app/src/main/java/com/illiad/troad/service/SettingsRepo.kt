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
            tStore.selectedCryptoFlow,   // 3
            tStore.jwtFlow,              // 4 (Maps to activeToken via JWT/JWT2 rules if needed)
            tStore.autoRenewFlow,        // 5
            tStore.durationFlow,         // 6
            tStore.sharedSecretFlow      // 7
        )

        return combine(flows) { array ->
            Settings(
                domain = array[0] as? String ?: "",
                sni = array[1] as? String ?: "",
                port = array[2] as? Int ?: 443,
                crypto = array[3] as? Cryptos ?: Cryptos.JWT,
                jwt = array[4] as? String, // Cleanly mapped from index 5
                autoRenew = AutoRenew.fromMinutes(
                    array[5] as? Long ?: 0L
                ), // Cleanly mapped from index 6
                duration = array[6] as? Duration, // Cleanly mapped from index 7
                secret = array[7] as? String // Cleanly mapped from index 8 (No more index 10 crash!)
            )
        }
            .distinctUntilChanged()
    }
}

