package com.cashierapp.photocheckout.ui.scan.edit

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.cashierapp.photocheckout.domain.model.DraftLine
import com.cashierapp.photocheckout.ui.theme.PhotoCheckoutTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

public class EditItemScreenTest {
    @get:Rule
    public val composeRule = createComposeRule()

    private fun line(): DraftLine =
        DraftLine(
            sku = "SKU-0002",
            name = "Es Kopi Susu",
            quantity = 2,
            unitPriceMinor = 15_000,
            lineTotalMinor = 30_000,
            confidence = 0.86f,
            lowConfidence = false,
        )

    @Test
    public fun incrementThenSaveReportsNewQuantity() {
        var savedQty = -1
        composeRule.setContent {
            PhotoCheckoutTheme {
                EditItemScreen(
                    line = line(),
                    onBack = {},
                    onSave = { qty, _ -> savedQty = qty },
                    onRemove = {},
                    resolvePhotoPath = { it },
                )
            }
        }

        composeRule.onNodeWithTag("edit-quantity-value").assertTextEquals("2")
        composeRule.onNodeWithTag("edit-qty-increment").performClick()
        composeRule.onNodeWithTag("edit-quantity-value").assertTextEquals("3")
        composeRule.onNodeWithTag("edit-save-button").performClick()
        assertEquals(3, savedQty)
    }

    @Test
    public fun removeButtonTriggersRemoval() {
        var removed = false
        composeRule.setContent {
            PhotoCheckoutTheme {
                EditItemScreen(
                    line = line(),
                    onBack = {},
                    onSave = { _, _ -> },
                    onRemove = { removed = true },
                    resolvePhotoPath = { it },
                )
            }
        }

        composeRule.onNodeWithTag("edit-remove-button").performClick()
        assertEquals(true, removed)
    }
}
