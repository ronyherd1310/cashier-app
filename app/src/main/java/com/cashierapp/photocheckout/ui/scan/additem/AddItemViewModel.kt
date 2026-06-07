package com.cashierapp.photocheckout.ui.scan.additem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashierapp.photocheckout.data.storage.PhotoStorage
import com.cashierapp.photocheckout.domain.catalog.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backs the Add-Item picker (C2): the active catalog filtered by a name/SKU query
 * (SCAN-8). It does not own the draft — the shared DraftViewModel applies the adds.
 */
@HiltViewModel
public class AddItemViewModel
    @Inject
    constructor(
        catalogRepository: CatalogRepository,
        private val photoStorage: PhotoStorage,
    ) : ViewModel() {
        private val query = MutableStateFlow("")

        public val uiState: StateFlow<AddItemUiState> =
            combine(catalogRepository.observeActiveProducts(), query) { products, q ->
                AddItemUiState(query = q, products = products.filter { matchesQuery(it, q) })
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = AddItemUiState(),
            )

        public fun onQueryChange(value: String) {
            query.value = value
        }

        public fun resolvePhotoPath(path: String): String = photoStorage.absolutePath(path)
    }
