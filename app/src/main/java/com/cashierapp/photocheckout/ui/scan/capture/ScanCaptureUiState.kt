package com.cashierapp.photocheckout.ui.scan.capture

import androidx.compose.runtime.Immutable
import com.cashierapp.photocheckout.domain.model.DraftReceipt
import com.cashierapp.photocheckout.domain.model.ScanStage

/** UI state for the Scan Capture screen (S1) and its staged processing overlay (C3). */
@Immutable
public data class ScanCaptureUiState(
    val phase: ScanCapturePhase = ScanCapturePhase.Ready,
    val stage: ScanStage? = null,
    val errorMessage: String? = null,
    val draft: DraftReceipt? = null,
)

public enum class ScanCapturePhase {
    Ready,
    Processing,
    Error,
}
