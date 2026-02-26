package com.phoenix.client.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phoenix.client.data.datastore.SplitTunnelDataStore
import com.phoenix.client.domain.model.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplitTunnelViewModel @Inject constructor(
    application: Application,
    private val dataStore: SplitTunnelDataStore,
) : AndroidViewModel(application) {

    val isEnabled: StateFlow<Boolean> = dataStore.enabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Package names of apps that BYPASS the VPN.
     * A switch shown as ON in the UI means the app goes through VPN (= NOT in this set).
     */
    val excludedApps: StateFlow<Set<String>> = dataStore.excludedAppsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val pm  = ctx.packageManager
            _installedApps.value = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null } // only user-visible apps
                .filter { it.packageName != ctx.packageName }                     // exclude Phoenix itself
                .mapNotNull { info ->
                    runCatching {
                        AppInfo(
                            packageName = info.packageName,
                            name        = pm.getApplicationLabel(info).toString(),
                        )
                    }.getOrNull()
                }
                .sortedBy { it.name.lowercase() }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setEnabled(enabled) }
    }

    fun toggleApp(packageName: String) {
        viewModelScope.launch { dataStore.toggleApp(packageName) }
    }

    /** Routes ALL listed apps through VPN (clears the excluded set). */
    fun includeAll() {
        viewModelScope.launch { dataStore.setExcludedApps(emptySet()) }
    }

    /** Excludes ALL listed apps from VPN (bypasses every app). */
    fun excludeAll() {
        viewModelScope.launch {
            dataStore.setExcludedApps(_installedApps.value.map { it.packageName }.toSet())
        }
    }
}
