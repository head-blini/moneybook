package com.moneybook

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneybook.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun enterHome() {
        compose.onNodeWithTag("screen_Login").assertIsDisplayed()
        compose.onNodeWithTag("nav_Home").assertDoesNotExist()
        compose.onNodeWithText("개발용 시작").performClick()
        compose.onNodeWithTag("screen_HouseholdSetup").assertIsDisplayed()
        compose.onNodeWithText("개발용 다음").performClick()
        compose.onNodeWithTag("screen_NotificationPermission").assertIsDisplayed()
        compose.onNodeWithText("개발용 홈으로").performClick()
        compose.onNodeWithTag("screen_Home").assertIsDisplayed()
    }

    @Test fun onboardingAndAllMainDestinations() {
        enterHome()
        compose.onNodeWithText("₩2,431,500").assertIsDisplayed()
        listOf("Transactions", "Statistics", "Settings", "Home").forEach { name ->
            compose.onNodeWithTag("nav_$name").performClick()
            compose.onNodeWithTag("screen_$name").assertIsDisplayed()
            compose.onNodeWithTag("nav_$name").assertIsSelected()
        }
        compose.onNodeWithTag("nav_Transactions").performClick()
        compose.onNodeWithTag("nav_Add").performClick()
        compose.onNodeWithTag("screen_AddTransaction").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithTag("screen_Transactions").assertIsDisplayed()
        compose.onNodeWithTag("nav_Add").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("screen_Transactions").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("screen_Home").assertIsDisplayed()
        compose.onNodeWithTag("screen_Login").assertDoesNotExist()
    }

    @Test fun recreationRetainsDestination() {
        enterHome()
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("screen_Settings").assertIsDisplayed()
        compose.onNodeWithTag("nav_Settings").assertIsSelected()
    }
}
