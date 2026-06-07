package com.cashierapp.photocheckout.ui.scan.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashierapp.photocheckout.domain.model.DraftReceipt
import com.cashierapp.photocheckout.ui.common.camera.CameraCaptureRoute

/**
 * Scan Capture (S1) entry point. Wires the reusable C1 camera to the capture
 * pipeline and hands the priced draft to [onDraftReady] when recognition succeeds.
 */
@Composable
public fun ScanCaptureRoute(
    onClose: () -> Unit,
    onDraftReady: (DraftReceipt) -> Unit,
    viewModel: ScanCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.draft) {
        val draft = state.draft
        if (draft != null) {
            onDraftReady(draft)
            viewModel.onDraftConsumed()
        }
    }

    ScanCaptureScreen(
        state = state,
        onCancelProcessing = viewModel::onCancel,
        onErrorDismissed = viewModel::onErrorDismissed,
        camera = {
            CameraCaptureRoute(
                onPhotoCaptured = viewModel::onPhotoCaptured,
                onClose = onClose,
            )
        },
    )
}
