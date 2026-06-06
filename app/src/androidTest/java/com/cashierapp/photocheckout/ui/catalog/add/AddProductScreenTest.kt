package com.cashierapp.photocheckout.ui.catalog.add

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cashierapp.photocheckout.ui.theme.PhotoCheckoutTheme
import org.junit.Rule
import org.junit.Test

public class AddProductScreenTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun reviewStepShowsEnteredProductDetails() {
        composeRule.setContent {
            PhotoCheckoutTheme {
                AddProductScreen(
                    state =
                        AddProductUiState(
                            step = 3,
                            name = "Nasi Goreng",
                            price = "25.000",
                            photoPath = "photo.jpg",
                        ),
                    onBack = {},
                    onNameChange = {},
                    onPriceChange = {},
                    onAddPhoto = {},
                    onNext = {},
                    onPrevious = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("Review").assertExists()
        composeRule.onNodeWithText("Nasi Goreng").assertExists()
        composeRule.onNodeWithText("25.000").assertExists()
        composeRule.onNodeWithText("Save Product").assertIsEnabled()
    }
}
