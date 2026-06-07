package com.cashierapp.photocheckout.ui.scan.additem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashierapp.photocheckout.ui.scan.draft.DraftViewModel

/**
 * Add-Item (C2) entry point. Lists the active catalog and applies adds to the shared
 * [DraftViewModel] so prices/totals stay deterministic. Returns to the draft afterward.
 */
@Composable
public fun AddItemRoute(
    onDone: () -> Unit,
    addItemViewModel: AddItemViewModel = hiltViewModel(),
    draftViewModel: DraftViewModel = hiltViewModel(),
) {
    val state by addItemViewModel.uiState.collectAsStateWithLifecycle()

    AddItemScreen(
        state = state,
        onClose = onDone,
        onQueryChange = addItemViewModel::onQueryChange,
        onQuickAdd = { item ->
            draftViewModel.addCatalogItem(item)
            onDone()
        },
        onAddSelected = { items ->
            items.forEach(draftViewModel::addCatalogItem)
            onDone()
        },
        resolvePhotoPath = addItemViewModel::resolvePhotoPath,
    )
}
