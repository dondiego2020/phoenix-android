package com.phoenix.client.domain.repository

import com.phoenix.client.domain.model.ClientConfig
import kotlinx.coroutines.flow.Flow

interface ConfigRepository {
    /** The currently selected config profile. */
    fun observeConfig(): Flow<ClientConfig>

    /** All stored config profiles. */
    fun observeConfigs(): Flow<List<ClientConfig>>

    /** Insert or update a config (matched by id). */
    suspend fun saveConfig(config: ClientConfig)

    /** Remove the config with this id. Always keeps at least one profile. */
    suspend fun deleteConfig(id: String)

    /** Make the config with this id the active profile. */
    suspend fun selectConfig(id: String)
}
