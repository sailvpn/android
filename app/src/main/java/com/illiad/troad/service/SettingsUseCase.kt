package com.illiad.troad.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

class SettingsUseCase(private val repository: SettingsRepo) {

    operator fun invoke(): Flow<Settings> {
        return repository.getSettingsStream()
            .filter { settings ->
                // Business Rule: Silently ignore invalid configurations
                // to prevent the VPN engine from crashing during disk loads
                settings.isValid
            }
    }
}
