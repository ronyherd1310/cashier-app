package com.cashierapp.photocheckout.ui.scan.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashierapp.photocheckout.domain.image.ImageDownscaler
import com.cashierapp.photocheckout.domain.model.ScanStage
import com.cashierapp.photocheckout.domain.usecase.ScanCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Scan Capture screen: downscale the capture (SCAN-2), run [ScanCounter]
 * while exposing a staged [ScanStage] for the processing overlay (SCAN-3), then hand
 * the priced draft to the UI. A recognizer failure surfaces a recoverable error and
 * never navigates to a partial draft (SCAN-9).
 */
@HiltViewModel
public class ScanCaptureViewModel
    @Inject
    constructor(
        private val downscaler: ImageDownscaler,
        private val scanCounter: ScanCounter,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ScanCaptureUiState())
        public val uiState: StateFlow<ScanCaptureUiState> = _uiState.asStateFlow()

        private var scanJob: Job? = null

        public fun onPhotoCaptured(jpegBytes: ByteArray) {
            scanJob?.cancel()
            scanJob =
                viewModelScope.launch {
                    _uiState.value =
                        ScanCaptureUiState(phase = ScanCapturePhase.Processing, stage = ScanStage.Uploading)
                    val image = downscaler.downscale(jpegBytes)
                    _uiState.update { it.copy(stage = ScanStage.Recognizing) }
                    val result = scanCounter(image)
                    result.fold(
                        onSuccess = { draft ->
                            _uiState.update { it.copy(stage = ScanStage.Pricing) }
                            _uiState.value = ScanCaptureUiState(phase = ScanCapturePhase.Ready, draft = draft)
                        },
                        onFailure = { error ->
                            _uiState.value =
                                ScanCaptureUiState(
                                    phase = ScanCapturePhase.Error,
                                    errorMessage = error.message ?: "Recognition failed. Please try again.",
                                )
                        },
                    )
                }
        }

        /** Cancel an in-flight scan and return to the ready capture state (SCAN-9). */
        public fun onCancel() {
            scanJob?.cancel()
            _uiState.value = ScanCaptureUiState()
        }

        /** Clear the produced draft once the UI has navigated to Draft Review. */
        public fun onDraftConsumed() {
            _uiState.update { it.copy(phase = ScanCapturePhase.Ready, draft = null) }
        }

        public fun onErrorDismissed() {
            _uiState.value = ScanCaptureUiState()
        }
    }
