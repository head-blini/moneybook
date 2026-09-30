package com.moneybook

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.moneybook.app.MainActivity
import com.moneybook.core.ui.MoneyBookTheme
import com.moneybook.feature.home.HomeScreen
import com.moneybook.feature.home.HomeUiState
import com.moneybook.domain.model.MonthlySummary
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThemeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun verifyTheme(dark: Boolean, dynamic: Boolean) {
        var backgroundLuminance = -1f
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MoneyBookTheme(darkTheme = dark, dynamicColor = dynamic) {
                    val background = MaterialTheme.colorScheme.background
                    SideEffect { backgroundLuminance = background.luminance() }
                    HomeScreen(HomeUiState(loading = false, summary = MonthlySummary(0, 2_431_500, 0, 0)))
                }
            }
        }
        compose.onNodeWithText("이번 달 지출  ₩2,431,500").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(if (dark) backgroundLuminance < 0.5f else backgroundLuminance > 0.5f)
        }
    }
    @Test fun lightThemeRendersHome() = verifyTheme(dark = false, dynamic = false)
    @Test fun darkThemeRendersHome() = verifyTheme(dark = true, dynamic = false)
    @Test fun dynamicLightThemeRendersHome() = verifyTheme(dark = false, dynamic = true)
    @Test fun dynamicDarkThemeRendersHome() = verifyTheme(dark = true, dynamic = true)
}
