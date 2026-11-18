package com.example.ui.components

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CineLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CineLogLogoTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `logo renders wordmark when enabled`() {
        composeTestRule.setContent {
            CineLogTheme {
                CineLogLogo(
                    size = 28.dp,
                    showWordmark = true,
                    animated = false,
                    wordmarkFontSize = 20.sp,
                    modifier = Modifier.testTag("logo_horizontal"),
                )
            }
        }

        composeTestRule.onNodeWithTag("logo_horizontal").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("CineLog").assertIsDisplayed()
        composeTestRule.onNodeWithText("CineLog", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `logo hides wordmark when showWordmark is false`() {
        composeTestRule.setContent {
            CineLogTheme {
                CineLogLogo(
                    size = 48.dp,
                    showWordmark = false,
                    animated = false,
                    modifier = Modifier.testTag("logo_icon_only"),
                )
            }
        }

        composeTestRule.onNodeWithTag("logo_icon_only").assertIsDisplayed()
        composeTestRule.onNodeWithText("CineLog", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `vertical animated logo completes entry animation cleanly`() {
        composeTestRule.setContent {
            CineLogTheme {
                CineLogLogo(
                    size = 72.dp,
                    showWordmark = true,
                    animated = true,
                    wordmarkFontSize = 34.sp,
                    layout = CineLogLogoLayout.Vertical,
                    showGlow = true,
                    modifier = Modifier.testTag("logo_login_animated"),
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onNodeWithTag("logo_login_animated").assertIsDisplayed()
        composeTestRule.onNodeWithText("CineLog", useUnmergedTree = true).assertIsDisplayed()
    }
}
