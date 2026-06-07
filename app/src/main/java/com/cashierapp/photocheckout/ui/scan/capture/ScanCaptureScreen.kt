package com.cashierapp.photocheckout.ui.scan.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cashierapp.photocheckout.ui.common.overlay.ProcessingOverlay
import com.cashierapp.photocheckout.ui.theme.AppDimens

private const val FRAMING_HINT =
    "Make sure items are clearly visible, not stacked, and well-lit"

/**
 * Scan Capture screen (S1, mockup 07). Hosts the camera via the [camera] slot and
 * layers the framing hint, staged processing overlay (C3), and a recoverable error
 * banner over it based on [state].
 */
@Composable
public fun ScanCaptureScreen(
    state: ScanCaptureUiState,
    onCancelProcessing: () -> Unit,
    onErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    camera: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        camera()

        if (state.phase == ScanCapturePhase.Ready) {
            FramingHintPill(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp, start = AppDimens.spaceLg, end = AppDimens.spaceLg),
            )
        }

        if (state.phase == ScanCapturePhase.Processing && state.stage != null) {
            ProcessingOverlay(stage = state.stage, onCancel = onCancelProcessing)
        }

        if (state.phase == ScanCapturePhase.Error) {
            ErrorBanner(
                message = state.errorMessage ?: "Recognition failed. Please try again.",
                onRetry = onErrorDismissed,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(AppDimens.screenPadding),
            )
        }
    }
}

@Composable
private fun FramingHintPill(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(AppDimens.controlRadius),
        color = Color.Black.copy(alpha = 0.5f),
        modifier = modifier.testTag("scan-framing-hint"),
    ) {
        Text(
            text = FRAMING_HINT,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(
                    horizontal = AppDimens.spaceMd,
                    vertical = AppDimens.spaceSm,
                ),
        )
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(AppDimens.cardRadius),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("scan-error-banner"),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.spaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            Button(onClick = onRetry, modifier = Modifier.testTag("scan-error-retry-button")) {
                Text(text = "Try again")
            }
        }
    }
}
