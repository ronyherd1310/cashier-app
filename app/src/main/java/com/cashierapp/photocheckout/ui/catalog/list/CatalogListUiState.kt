package com.cashierapp.photocheckout.ui.catalog.list

import androidx.compose.runtime.Immutable
import com.cashierapp.photocheckout.domain.model.CatalogItem

@Immutable
public data class CatalogListUiState(
    val activeProducts: List<CatalogItem> = emptyList(),
)
