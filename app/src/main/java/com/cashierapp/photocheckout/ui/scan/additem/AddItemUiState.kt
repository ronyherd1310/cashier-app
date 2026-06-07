package com.cashierapp.photocheckout.ui.scan.additem

import androidx.compose.runtime.Immutable
import com.cashierapp.photocheckout.domain.model.CatalogItem

/** UI state for the full-screen Add-Item catalog picker (C2, mockups 11/12). */
@Immutable
public data class AddItemUiState(
    val query: String = "",
    val products: List<CatalogItem> = emptyList(),
)

/** Active-catalog match on name + SKU (case-insensitive), the search scope per spec. */
public fun matchesQuery(
    item: CatalogItem,
    query: String,
): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return item.name.contains(trimmed, ignoreCase = true) ||
        item.sku.contains(trimmed, ignoreCase = true)
}
