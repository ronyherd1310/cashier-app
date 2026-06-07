package com.cashierapp.photocheckout.ui.scan.draft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashierapp.photocheckout.domain.model.DraftReceipt

/**
 * Draft Review (S2) entry point. Seeds the shared [DraftViewModel] with the draft
 * the capture flow produced and renders [DraftScreen]. Edit/Add/Discard navigation
 * is supplied by the host shell.
 */
@Composable
public fun DraftRoute(
    draft: DraftReceipt,
    onBack: () -> Unit,
    onLineClick: (String) -> Unit,
    onAddItem: () -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    viewModel: DraftViewModel = hiltViewModel(),
) {
    LaunchedEffect(draft) { viewModel.setDraft(draft) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DraftScreen(
        state = state,
        onBack = onBack,
        onLineClick = onLineClick,
        onAddItem = onAddItem,
        onConfirm = onConfirm,
        onDiscard = onDiscard,
        resolvePhotoPath = viewModel::resolvePhotoPath,
    )
}
