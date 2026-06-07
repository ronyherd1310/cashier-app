package com.cashierapp.photocheckout.ui.scan.draft

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.cashierapp.photocheckout.domain.model.DraftLine
import com.cashierapp.photocheckout.domain.model.DraftReceipt
import com.cashierapp.photocheckout.domain.model.UnidentifiedItem
import com.cashierapp.photocheckout.ui.theme.PhotoCheckoutTheme
import org.junit.Rule
import org.junit.Test

public class DraftScreenTest {
    @get:Rule
    public val composeRule = createComposeRule()

    private fun draft(): DraftReceipt =
        DraftReceipt(
            lines =
                listOf(
                    DraftLine(
                        sku = "SKU-0001",
                        name = "Nasi Goreng",
                        quantity = 1,
                        unitPriceMinor = 25_000,
                        lineTotalMinor = 25_000,
                        confidence = 0.92f,
                        lowConfidence = false,
                    ),
                    DraftLine(
                        sku = "SKU-0004",
                        name = "Sate Ayam",
                        quantity = 2,
                        unitPriceMinor = 20_000,
                        lineTotalMinor = 40_000,
                        confidence = 0.45f,
                        lowConfidence = true,
                    ),
                ),
            unidentified = listOf(UnidentifiedItem(rawSku = "SKU-9999", quantity = 1, confidence = 0.2f)),
            subtotalMinor = 65_000,
            taxMinor = 0,
            totalMinor = 65_000,
        )

    private fun setScreen() {
        composeRule.setContent {
            PhotoCheckoutTheme {
                DraftScreen(
                    state = DraftUiState(draft = draft()),
                    onBack = {},
                    onLineClick = {},
                    onAddItem = {},
                    onConfirm = {},
                    onDiscard = {},
                    resolvePhotoPath = { it },
                )
            }
        }
    }

    @Test
    public fun showsItemCountAndTotal() {
        setScreen()
        composeRule.onNodeWithText("Draft (2 item)").assertIsDisplayed()
        composeRule.onNodeWithTag("draft-total").assertExists()
    }

    @Test
    public fun rendersNumericConfidencePerLine() {
        setScreen()
        composeRule.onNodeWithTag("draft-confidence-SKU-0001", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("0.92", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("0.45", useUnmergedTree = true).assertExists()
    }

    @Test
    public fun flagsLowConfidenceLineWithWarning() {
        setScreen()
        composeRule.onNodeWithTag("draft-low-confidence-SKU-0004", useUnmergedTree = true).assertExists()
    }

    @Test
    public fun surfacesUnidentifiedItem() {
        setScreen()
        composeRule.onNodeWithTag("draft-unidentified-0").assertIsDisplayed()
        composeRule.onNodeWithText("Unidentified item").assertIsDisplayed()
    }
}
