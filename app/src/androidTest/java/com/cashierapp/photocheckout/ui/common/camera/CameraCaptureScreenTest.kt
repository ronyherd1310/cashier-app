package com.cashierapp.photocheckout.ui.common.camera

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.cashierapp.photocheckout.ui.theme.PhotoCheckoutTheme
import org.junit.Rule
import org.junit.Test

public class CameraCaptureScreenTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun permissionDeniedStateShowsRecoverableRationale() {
        composeRule.setContent {
            PhotoCheckoutTheme {
                CameraCaptureScreen(
                    state = CameraCaptureUiState.PermissionDenied,
                    onCaptureClick = {},
                    onRequestPermissionClick = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithText("Camera permission required").assertExists()
        composeRule.onNodeWithText("Grant permission").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    public fun readyStateShowsCaptureAndCloseControls() {
        composeRule.setContent {
            PhotoCheckoutTheme {
                CameraCaptureScreen(
                    state = CameraCaptureUiState.Ready,
                    onCaptureClick = {},
                    onRequestPermissionClick = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("camera-capture-button").assertExists()
        composeRule.onNodeWithTag("camera-close-button").assertExists()
    }
}
