package com.cashierapp.photocheckout.ui.catalog.list

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.model.ProductPhoto
import com.cashierapp.photocheckout.ui.theme.PhotoCheckoutTheme
import org.junit.Rule
import org.junit.Test

public class CatalogListScreenTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun rendersProductCardsAndActiveCount() {
        composeRule.setContent {
            PhotoCheckoutTheme {
                CatalogListScreen(
                    state =
                        CatalogListUiState(
                            activeProducts =
                                listOf(
                                    catalogItem(
                                        name = "Nasi Goreng Spesial",
                                        sku = "SKU-0001",
                                        priceMinor = 25_000L,
                                    ),
                                    catalogItem(
                                        name = "Es Kopi Susu",
                                        sku = "SKU-0002",
                                        priceMinor = 15_000L,
                                    ),
                                ),
                        ),
                    onAddProductClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("catalog-active-count").assertTextEquals("2 active items")
        composeRule.onNodeWithText("Nasi Goreng Spesial").assertExists()
        composeRule.onNodeWithText("SKU-0001").assertExists()
        composeRule.onNodeWithText("IDR 25.000").assertExists()
        composeRule.onNodeWithText("Es Kopi Susu").assertExists()
        composeRule.onNodeWithTag("catalog-card-menu-SKU-0001").assertExists()
    }

    @Test
    public fun rendersEmptyStateWhenNoProductsExist() {
        composeRule.setContent {
            PhotoCheckoutTheme {
                CatalogListScreen(
                    state = CatalogListUiState(activeProducts = emptyList()),
                    onAddProductClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("catalog-active-count").assertTextEquals("0 active items")
        composeRule.onNodeWithText("No products yet").assertExists()
        composeRule.onNodeWithText("Add your first product to start checkout.").assertExists()
    }

    private fun catalogItem(
        name: String,
        sku: String,
        priceMinor: Long,
    ): CatalogItem =
        CatalogItem(
            id = sku.takeLast(4).toLong(),
            sku = sku,
            name = name,
            priceMinor = priceMinor,
            active = true,
            photos = listOf(ProductPhoto(path = "product.jpg", position = 0)),
            createdAtEpochMillis = 1L,
        )
}
