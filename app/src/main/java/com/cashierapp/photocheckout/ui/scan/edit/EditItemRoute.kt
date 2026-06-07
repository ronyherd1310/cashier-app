package com.cashierapp.photocheckout.ui.scan.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashierapp.photocheckout.ui.scan.draft.DraftViewModel

/**
 * Edit-Item (mockup 10) entry point. Edits the targeted line through the shared
 * [DraftViewModel] so totals recompute in one place. Returns to the draft on save,
 * remove, or back.
 */
@Composable
public fun EditItemRoute(
    sku: String,
    onDone: () -> Unit,
    viewModel: DraftViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val line = state.lines.firstOrNull { it.sku == sku }

    LaunchedEffect(line == null) {
        if (line == null) onDone()
    }

    if (line != null) {
        EditItemScreen(
            line = line,
            onBack = onDone,
            onSave = { quantity, note ->
                viewModel.updateLine(sku = sku, quantity = quantity, note = note)
                onDone()
            },
            onRemove = {
                viewModel.removeLine(sku)
                onDone()
            },
            resolvePhotoPath = viewModel::resolvePhotoPath,
        )
    }
}
