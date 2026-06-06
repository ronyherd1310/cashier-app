package com.cashierapp.photocheckout.ui.catalog.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    ProductDetailScreen(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onPriceChange = viewModel::onPriceChange,
        onSave = viewModel::saveEdits,
        onAddPhoto = viewModel::addPlaceholderPhoto,
        onRemovePhoto = viewModel::removeLastPhoto,
        onToggleActive = viewModel::toggleActive,
    )
}
