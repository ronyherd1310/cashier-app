package com.cashierapp.photocheckout.ui.catalog.add

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
public fun AddProductRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddProductViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AddProductScreen(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onPriceChange = viewModel::onPriceChange,
        onAddPhoto = viewModel::addPlaceholderPhoto,
        onNext = viewModel::nextStep,
        onPrevious = viewModel::previousStep,
        onSave = { viewModel.save(onSaved) },
    )
}
