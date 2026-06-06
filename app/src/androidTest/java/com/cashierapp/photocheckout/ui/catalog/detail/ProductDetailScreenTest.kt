package com.cashierapp.photocheckout.ui.catalog.detail

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.model.ProductPhoto
import com.cashierapp.photocheckout.ui.theme.PhotoCheckoutTheme
import org.junit.Rule
import org.junit.Test

public class ProductDetailScreenTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun rendersEditableProductDetail() {
        composeRule.setContent {
            PhotoCheckoutTheme {
                ProductDetailScreen(
                    state =
                        ProductDetailUiState(
                            product =
                                CatalogItem(
                                    id = 1L,
                                    sku = "SKU-0001",
                                    name = "Nasi Goreng",
                                    priceMinor = 25_000L,
                                    active = true,
                                    photos = listOf(ProductPhoto("photo.jpg", 0)),
                                    createdAtEpochMillis = 1L,
                                ),
                            nameInput = "Nasi Goreng",
                            priceInput = "25.000",
                        ),
                    onBack = {},
                    onNameChange = {},
                    onPriceChange = {},
                    onSave = {},
                    onAddPhoto = {},
                    onRemovePhoto = {},
                    onToggleActive = {},
                )
            }
        }

        composeRule.onNodeWithText("Product Detail").assertExists()
        composeRule.onNodeWithText("SKU • SKU-0001").assertExists()
        composeRule.onNodeWithText("Current: IDR 25.000").assertExists()
        composeRule.onNodeWithText("Photos: 1 of 3 photos").assertExists()
        composeRule.onNodeWithText("Edit Product").assertExists()
        composeRule.onNodeWithText("Deactivate").assertExists()
    }
}
