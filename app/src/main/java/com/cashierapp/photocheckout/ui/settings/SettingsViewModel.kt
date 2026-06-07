package com.cashierapp.photocheckout.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.cashierapp.photocheckout.data.config.RecognizerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@Immutable
public data class SettingsUiState(
    val apiKey: String = "",
    val modelId: String = "",
    val hasApiKey: Boolean = false,
    val saved: Boolean = false,
)

/**
 * Minimal Settings (not the full S7): enter the OpenRouter key + model id so a real
 * scan can run on a device. The key is held only transiently in UI state and written
 * to secure storage via [RecognizerConfig] (T9).
 */
@HiltViewModel
public class SettingsViewModel
    @Inject
    constructor(
        private val config: RecognizerConfig,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                SettingsUiState(
                    apiKey = config.apiKey.orEmpty(),
                    modelId = config.modelId,
                    hasApiKey = config.hasApiKey(),
                ),
            )
        public val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        public fun onApiKeyChange(value: String) {
            _uiState.value = _uiState.value.copy(apiKey = value, saved = false)
        }

        public fun onModelIdChange(value: String) {
            _uiState.value = _uiState.value.copy(modelId = value, saved = false)
        }

        public fun save() {
            config.setApiKey(_uiState.value.apiKey)
            config.setModelId(_uiState.value.modelId)
            _uiState.value = _uiState.value.copy(hasApiKey = config.hasApiKey(), saved = true)
        }
    }
