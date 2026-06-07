package com.cashierapp.photocheckout.ui.scan.additem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.ui.theme.PhotoCheckoutTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

public class AddItemScreenTest {
    @get:Rule
    public val composeRule = createComposeRule()

    private fun item(
        sku: String,
        name: String,
    ): CatalogItem =
        CatalogItem(
            id = sku.hashCode().toLong(),
            sku = sku,
            name = name,
            priceMinor = 10_000,
            active = true,
            photos = emptyList(),
            createdAtEpochMillis = 0L,
        )

    private val products = listOf(item("SKU-0001", "Coffee"), item("SKU-0002", "Tea"))

    @Test
    public fun quickAddReportsTappedItem() {
        var added: CatalogItem? = null
        composeRule.setContent {
            PhotoCheckoutTheme {
                AddItemScreen(
                    state = AddItemUiState(products = products),
                    onClose = {},
                    onQueryChange = {},
                    onQuickAdd = { added = it },
                    onAddSelected = {},
                    resolvePhotoPath = { it },
                )
            }
        }

        composeRule.onNodeWithTag("add-item-quick-add-SKU-0001").performClick()
        assertEquals("SKU-0001", added?.sku)
    }

    @Test
    public fun searchFieldEmitsQueryChanges() {
        var query = ""
        composeRule.setContent {
            PhotoCheckoutTheme {
                AddItemScreen(
                    state = AddItemUiState(products = products),
                    onClose = {},
                    onQueryChange = { query = it },
                    onQuickAdd = {},
                    onAddSelected = {},
                    resolvePhotoPath = { it },
                )
            }
        }

        composeRule.onNodeWithTag("add-item-search").performTextInput("tea")
        assertEquals("tea", query)
    }

    @Test
    public fun multiSelectAddsAllSelectedItems() {
        var addedCount = -1
        composeRule.setContent {
            PhotoCheckoutTheme {
                AddItemScreen(
                    state = AddItemUiState(products = products),
                    onClose = {},
                    onQueryChange = {},
                    onQuickAdd = {},
                    onAddSelected = { addedCount = it.size },
                    resolvePhotoPath = { it },
                )
            }
        }

        // Long-press (click in non-selecting mode) enters selection and selects the first row.
        composeRule.onNodeWithTag("add-item-row-SKU-0001").performClick()
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNodeWithTag("add-item-row-SKU-0002").performClick()
        composeRule.onNodeWithTag("add-item-add-to-draft-button").performClick()

        assertEquals(2, addedCount)
    }
}
