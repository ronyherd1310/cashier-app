package com.cashierapp.photocheckout.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
public fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onApiKeyChange = viewModel::onApiKeyChange,
        onModelIdChange = viewModel::onModelIdChange,
        onSave = viewModel::save,
    )
}
