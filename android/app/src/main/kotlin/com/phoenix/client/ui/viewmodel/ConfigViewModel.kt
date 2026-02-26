package com.phoenix.client.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phoenix.client.domain.model.ClientConfig
import com.phoenix.client.domain.repository.ConfigRepository
import com.phoenix.client.util.KeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ConfigUiState(
    val isGeneratingKeys: Boolean = false,
    /** Non-null after key generation succeeds — shown in the PublicKeyDialog. */
    val generatedPublicKey: String? = null,
    val keyGenError: String? = null,
    /** Emitted when generateKeys() succeeds — consumed by ConfigEditContent to update local form state. */
    val generatedPrivateKeyFile: String? = null,
    /** Emitted when onKeyFilePicked() succeeds — consumed by ConfigEditContent to update local form state. */
    val pickedKeyFile: String? = null,
)

@HiltViewModel
class ConfigViewModel @Inject constructor(
    application: Application,
    private val configRepository: ConfigRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    /** The currently selected config profile. */
    val config: StateFlow<ClientConfig> = configRepository
        .observeConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientConfig())

    /** All stored config profiles (drives the config list). */
    val configs: StateFlow<List<ClientConfig>> = configRepository
        .observeConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Config list operations ─────────────────────────────────────────────────

    fun selectConfig(id: String) {
        viewModelScope.launch { configRepository.selectConfig(id) }
    }

    fun deleteConfig(id: String) {
        viewModelScope.launch {
            val cfg = configs.value.firstOrNull { it.id == id }
            if (cfg != null && cfg.privateKeyFile == KeyManager.keyFileNameFor(id)) {
                File(getApplication<Application>().filesDir, cfg.privateKeyFile).delete()
            }
            configRepository.deleteConfig(id)
        }
    }

    // ── Form save ──────────────────────────────────────────────────────────────

    /** Persists [config] to DataStore and makes it the active profile. */
    fun save(config: ClientConfig) {
        viewModelScope.launch {
            configRepository.saveConfig(config)
            configRepository.selectConfig(config.id)
        }
    }

    // ── Key management ─────────────────────────────────────────────────────────

    /**
     * Runs the Go binary with `-gen-keys` and writes the key file to filesDir.
     * Results are emitted via [ConfigUiState] and NOT auto-saved to DataStore —
     * ConfigEditContent updates its local form state and saves on the next explicit Save.
     */
    fun generateKeys(configId: String) {
        if (_uiState.value.isGeneratingKeys) return
        _uiState.update { it.copy(isGeneratingKeys = true, keyGenError = null, generatedPublicKey = null) }

        viewModelScope.launch {
            runCatching {
                KeyManager.generateKeys(getApplication(), configId)
            }.onSuccess { pair ->
                _uiState.update {
                    it.copy(
                        isGeneratingKeys = false,
                        generatedPublicKey = pair.publicKey,
                        generatedPrivateKeyFile = pair.privateKeyFile,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isGeneratingKeys = false,
                        keyGenError = e.message ?: "Unknown error",
                    )
                }
            }
        }
    }

    /**
     * Copies the file at [uri] into the app's private filesDir.
     * Result is emitted via [ConfigUiState.pickedKeyFile] and NOT auto-saved to DataStore.
     */
    fun onKeyFilePicked(uri: Uri, configId: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()
                    val keyFileName = KeyManager.keyFileNameFor(configId)
                    val destFile = File(ctx.filesDir, keyFileName)
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { input.copyTo(it) }
                    } ?: throw IllegalStateException("Cannot open selected file")
                    keyFileName
                }
            }.onSuccess { keyFileName ->
                _uiState.update { it.copy(pickedKeyFile = keyFileName) }
            }.onFailure { e ->
                _uiState.update { it.copy(keyGenError = "Failed to import key: ${e.message}") }
            }
        }
    }

    /** Clear key file events after ConfigEditContent has consumed them. */
    fun consumeKeyFileEvents() {
        _uiState.update { it.copy(generatedPrivateKeyFile = null, pickedKeyFile = null) }
    }

    fun dismissPublicKeyDialog() {
        _uiState.update { it.copy(generatedPublicKey = null, keyGenError = null) }
    }
}
