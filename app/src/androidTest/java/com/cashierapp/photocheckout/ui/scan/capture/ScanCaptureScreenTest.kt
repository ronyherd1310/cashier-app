package com.cashierapp.photocheckout.ui.scan.capture

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.cashierapp.photocheckout.domain.model.ScanStage
import com.cashierapp.photocheckout.ui.theme.PhotoCheckoutTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

public class ScanCaptureScreenTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun readyStateShowsFramingHint() {
        composeRule.setContent {
            PhotoCheckoutTheme {
                ScanCaptureScreen(
                    state = ScanCaptureUiState(phase = ScanCapturePhase.Ready),
                    onCancelProcessing = {},
                    onErrorDismissed = {},
                    camera = { Text("camera") },
                )
            }
        }

        composeRule.onNodeWithTag("scan-framing-hint").assertIsDisplayed()
    }

    @Test
    public fun processingStateShowsStagedOverlay() {
        composeRule.setContent {
            PhotoCheckoutTheme {
                ScanCaptureScreen(
                    state = ScanCaptureUiState(phase = ScanCapturePhase.Processing, stage = ScanStage.Recognizing),
                    onCancelProcessing = {},
                    onErrorDismissed = {},
                    camera = { Text("camera") },
                )
            }
        }

        composeRule.onNodeWithTag("processing-overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("processing-cancel-button").assertIsDisplayed()
    }

    @Test
    public fun errorStateShowsRecoverableBannerAndRetries() {
        var retried = false
        composeRule.setContent {
            PhotoCheckoutTheme {
                ScanCaptureScreen(
                    state = ScanCaptureUiState(phase = ScanCapturePhase.Error, errorMessage = "network down"),
                    onCancelProcessing = {},
                    onErrorDismissed = { retried = true },
                    camera = { Text("camera") },
                )
            }
        }

        composeRule.onNodeWithTag("scan-error-banner").assertIsDisplayed()
        composeRule.onNodeWithTag("scan-error-retry-button").performClick()
        assertTrue(retried)
    }
}
