package com.cashierapp.photocheckout.ui.catalog.list

import androidx.compose.runtime.Immutable
import com.cashierapp.photocheckout.domain.model.CatalogItem

@Immutable
public data class CatalogListUiState(
    val products: List<CatalogItem> = emptyList(),
    val query: String = "",
    val statusFilter: CatalogStatusFilter = CatalogStatusFilter.ActiveOnly,
    val sortOrder: CatalogSortOrder = CatalogSortOrder.NameAscending,
) {
    public val visibleProducts: List<CatalogItem> =
        filterAndSortProducts(
            products = products,
            query = query,
            statusFilter = statusFilter,
            sortOrder = sortOrder,
        )

    public val activeCount: Int = products.count { it.active }
}
