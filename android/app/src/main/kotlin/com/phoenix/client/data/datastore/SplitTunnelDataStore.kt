package com.phoenix.client.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.splitTunnelStore: DataStore<Preferences> by preferencesDataStore(name = "split_tunnel")

/**
 * Persists split-tunnel settings:
 *   split_tunnel_enabled – whether split tunnelling is active
 *   excluded_apps        – package names of apps that BYPASS the VPN tunnel
 *
 * When split tunnel is OFF all apps go through VPN (except Phoenix itself).
 * When split tunnel is ON, every package in [excludedAppsFlow] bypasses the VPN.
 */
@Singleton
class SplitTunnelDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENABLED       = booleanPreferencesKey("split_tunnel_enabled")
        val EXCLUDED_APPS = stringSetPreferencesKey("excluded_apps")
    }

    val enabledFlow: Flow<Boolean> = context.splitTunnelStore.data
        .map { it[Keys.ENABLED] ?: false }

    val excludedAppsFlow: Flow<Set<String>> = context.splitTunnelStore.data
        .map { it[Keys.EXCLUDED_APPS] ?: emptySet() }

    suspend fun setEnabled(enabled: Boolean) {
        context.splitTunnelStore.edit { it[Keys.ENABLED] = enabled }
    }

    /** Adds or removes [packageName] from the excluded set. */
    suspend fun toggleApp(packageName: String) {
        context.splitTunnelStore.edit { prefs ->
            val current = prefs[Keys.EXCLUDED_APPS] ?: emptySet()
            prefs[Keys.EXCLUDED_APPS] =
                if (packageName in current) current - packageName else current + packageName
        }
    }

    /** Replaces the entire excluded-apps set at once (used for select-all / deselect-all). */
    suspend fun setExcludedApps(apps: Set<String>) {
        context.splitTunnelStore.edit { it[Keys.EXCLUDED_APPS] = apps }
    }
}
