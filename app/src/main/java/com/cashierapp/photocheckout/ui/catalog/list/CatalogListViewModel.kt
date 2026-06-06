package com.cashierapp.photocheckout.ui.catalog.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashierapp.photocheckout.data.db.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
public class CatalogListViewModel
    @Inject
    constructor(
        productRepository: ProductRepository,
    ) : ViewModel() {
        public val uiState: StateFlow<CatalogListUiState> =
            productRepository
                .observeActiveProducts()
                .map { products -> CatalogListUiState(activeProducts = products) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                    initialValue = CatalogListUiState(),
                )
    }
