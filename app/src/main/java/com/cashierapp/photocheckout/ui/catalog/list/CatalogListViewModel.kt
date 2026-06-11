package com.cashierapp.photocheckout.ui.catalog.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashierapp.photocheckout.data.storage.PhotoStorage
import com.cashierapp.photocheckout.domain.catalog.CatalogRepository
import com.cashierapp.photocheckout.domain.usecase.SetProductActive
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
public class CatalogListViewModel
    @Inject
    constructor(
        catalogRepository: CatalogRepository,
        private val setProductActive: SetProductActive,
        private val photoStorage: PhotoStorage,
    ) : ViewModel() {
        private val filters = MutableStateFlow(CatalogListFilters())

        public fun resolvePhotoPath(path: String): String = photoStorage.absolutePath(path)

        public val uiState: StateFlow<CatalogListUiState> =
            combine(
                catalogRepository.observeProducts(),
                filters,
            ) { products, filters ->
                CatalogListUiState(
                    products = products,
                    query = filters.query,
                    statusFilter = filters.statusFilter,
                    sortOrder = filters.sortOrder,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = CatalogListUiState(),
            )

        public fun onQueryChange(query: String) {
            filters.value = filters.value.copy(query = query)
        }

        public fun onStatusFilterChange(statusFilter: CatalogStatusFilter) {
            filters.value = filters.value.copy(statusFilter = statusFilter)
        }

        public fun onSortOrderChange(sortOrder: CatalogSortOrder) {
            filters.value = filters.value.copy(sortOrder = sortOrder)
        }

        public fun setActive(
            productId: Long,
            active: Boolean,
        ) {
            viewModelScope.launch {
                setProductActive(
                    productId = productId,
                    active = active,
                    changedAt = System.currentTimeMillis(),
                )
            }
        }
    }

private data class CatalogListFilters(
    val query: String = "",
    val statusFilter: CatalogStatusFilter = CatalogStatusFilter.ActiveOnly,
    val sortOrder: CatalogSortOrder = CatalogSortOrder.NameAscending,
)
