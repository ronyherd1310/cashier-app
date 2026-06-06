package com.cashierapp.photocheckout.ui.catalog.list

import com.cashierapp.photocheckout.domain.model.CatalogItem

public enum class CatalogStatusFilter {
    All,
    ActiveOnly,
    InactiveOnly,
}

public enum class CatalogSortOrder {
    NameAscending,
    NameDescending,
    PriceAscending,
    PriceDescending,
    NewestFirst,
    OldestFirst,
}

public fun filterAndSortProducts(
    products: List<CatalogItem>,
    query: String,
    statusFilter: CatalogStatusFilter,
    sortOrder: CatalogSortOrder,
): List<CatalogItem> {
    val normalizedQuery = query.trim().lowercase()
    return products
        .asSequence()
        .filter { product ->
            when (statusFilter) {
                CatalogStatusFilter.All -> true
                CatalogStatusFilter.ActiveOnly -> product.active
                CatalogStatusFilter.InactiveOnly -> !product.active
            }
        }.filter { product ->
            normalizedQuery.isEmpty() ||
                product.name.lowercase().contains(normalizedQuery) ||
                product.sku.lowercase().contains(normalizedQuery)
        }.let { sequence ->
            when (sortOrder) {
                CatalogSortOrder.NameAscending -> sequence.sortedBy { it.name.lowercase() }
                CatalogSortOrder.NameDescending -> sequence.sortedByDescending { it.name.lowercase() }
                CatalogSortOrder.PriceAscending -> sequence.sortedBy { it.priceMinor }
                CatalogSortOrder.PriceDescending -> sequence.sortedByDescending { it.priceMinor }
                CatalogSortOrder.NewestFirst -> sequence.sortedByDescending { it.createdAtEpochMillis }
                CatalogSortOrder.OldestFirst -> sequence.sortedBy { it.createdAtEpochMillis }
            }
        }.toList()
}
