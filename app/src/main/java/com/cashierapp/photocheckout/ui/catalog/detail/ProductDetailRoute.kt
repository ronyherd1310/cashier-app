package com.cashierapp.photocheckout.ui.catalog.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashierapp.photocheckout.ui.common.camera.CameraCaptureRoute

@Composable
public fun ProductDetailRoute(
    productId: Long,
    onBack: () -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(productId) {
        viewModel.load(productId)
    }
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

    ProductDetailScreen(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onPriceChange = viewModel::onPriceChange,
        onSave = viewModel::saveEdits,
        onAddPhoto = { showCamera = true },
        onRemovePhoto = viewModel::removeLastPhoto,
        onToggleActive = viewModel::toggleActive,
        resolvePhotoPath = viewModel::resolvePhotoPath,
    )
}
