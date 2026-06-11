package com.cashierapp.photocheckout.ui.catalog.add

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashierapp.photocheckout.ui.common.camera.CameraCaptureRoute

@Composable
public fun AddProductRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddProductViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCamera by rememberSaveable { mutableStateOf(false) }

    if (showCamera) {
        CameraCaptureRoute(
            onPhotoCaptured = { bytes ->
                viewModel.onPhotoCaptured(bytes)
                showCamera = false
            },
            onClose = { showCamera = false },
        )
        return
    }

    AddProductScreen(
        state = state,
        onBack = {
            viewModel.reset()
            onBack()
        },
        onNameChange = viewModel::onNameChange,
        onPriceChange = viewModel::onPriceChange,
        onAddPhoto = { showCamera = true },
        onNext = viewModel::nextStep,
        onPrevious = viewModel::previousStep,
        onSave = {
            viewModel.save {
                viewModel.reset()
                onSaved()
            }
        },
    )
}
