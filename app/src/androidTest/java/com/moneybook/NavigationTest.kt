package com.moneybook

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneybook.app.MainActivity
import com.moneybook.app.navigation.MoneyBookMainNavigation
import com.moneybook.core.ui.MoneyBookTheme
import com.moneybook.domain.model.Household
import com.moneybook.domain.model.HouseholdMember
import com.moneybook.feature.auth.AuthUiState
import com.moneybook.feature.auth.LoginScreen
import com.moneybook.feature.onboarding.HouseholdSetupScreen
import com.moneybook.feature.onboarding.HouseholdSetupUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun loginFormEmitsCredentials() {
        var request: Triple<String, String, String>? = null
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MoneyBookTheme {
                    LoginScreen(
                        AuthUiState(),
                        { email, password -> request = Triple("in", email, password) },
                        { email, password -> request = Triple("up", email, password) },
                    )
                }
            }
        }
        compose.onNodeWithTag("auth_email").performTextInput("two@example.com")
        compose.onNodeWithTag("auth_password").performTextInput("secret12")
        compose.onNodeWithTag("auth_sign_in").performClick()
        compose.runOnIdle { assertEquals(Triple("in", "two@example.com", "secret12"), request) }
    }

    @Test fun householdSetupProvidesCreateAndJoin() {
        var action = ""
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MoneyBookTheme {
                    HouseholdSetupScreen(
                        HouseholdSetupUiState(),
                        { action = "create:$it" },
                        { action = "join:$it" },
                        { action = "signout" },
                    )
                }
            }
        }
        compose.onNodeWithTag("household_name").performTextInput("우리집")
        compose.onNodeWithTag("household_create").performClick()
        compose.runOnIdle { assertEquals("create:우리집", action) }
        compose.onNodeWithTag("invitation_code").performTextInput("abc123")
        compose.onNodeWithTag("household_join").performClick()
        compose.runOnIdle { assertEquals("join:ABC123", action) }
    }

    @Test fun authenticatedMainDestinationsStillWork() {
        val household = Household("home-id", "우리집", listOf(HouseholdMember("user-one", "OWNER")))
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { MoneyBookTheme { MoneyBookMainNavigation(household, {}, {}) } }
        }
        compose.onNodeWithTag("screen_Home").assertIsDisplayed()
        compose.onNodeWithText("우리집 · 구성원 1/2").assertIsDisplayed()
        listOf("Transactions", "Statistics", "Settings", "Home").forEach { name ->
            compose.onNodeWithTag("nav_$name").performClick()
            compose.onNodeWithTag("screen_$name").assertIsDisplayed()
            compose.onNodeWithTag("nav_$name").assertIsSelected()
        }
        compose.onNodeWithTag("nav_Add").performClick()
        compose.onNodeWithTag("screen_AddTransaction").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithTag("screen_Home").assertIsDisplayed()
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithTag("create_invitation").assertIsDisplayed()
        compose.onNodeWithText("구성원 1/2").assertIsDisplayed()
    }
}
