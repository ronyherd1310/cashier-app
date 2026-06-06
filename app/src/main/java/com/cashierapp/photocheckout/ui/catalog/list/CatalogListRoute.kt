package com.cashierapp.photocheckout.ui.catalog.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
public fun CatalogListRoute(
    onAddProductClick: () -> Unit,
    viewModel: CatalogListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CatalogListScreen(
        state = state,
        onAddProductClick = onAddProductClick,
    )
}
