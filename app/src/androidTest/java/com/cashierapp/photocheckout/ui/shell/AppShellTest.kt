package com.cashierapp.photocheckout.ui.shell

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cashierapp.photocheckout.ui.theme.PhotoCheckoutTheme
import org.junit.Rule
import org.junit.Test

public class AppShellTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun catalogueIsSelectedByDefault() {
        composeRule.setContent {
            PhotoCheckoutTheme {
                AppShell()
            }
        }

        composeRule.onNodeWithTag("tab-Catalogue").assertIsSelected()
        composeRule.onNodeWithText("Catalogue").assertExists()
        composeRule.onNodeWithTag("shell-title").assertTextEquals("Catalogue")
    }

    @Test
    public fun selectingTabShowsPlaceholder() {
        composeRule.setContent {
            PhotoCheckoutTheme {
                AppShell()
            }
        }

        composeRule.onNodeWithTag("tab-Sales").performClick()

        composeRule.onNodeWithTag("tab-Sales").assertIsSelected()
        composeRule.onNodeWithTag("shell-title").assertTextEquals("Sales")
        composeRule.onNodeWithText("Sales placeholder").assertExists()
    }
}
