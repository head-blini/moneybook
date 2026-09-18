package com.moneybook.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.moneybook.app.AppUiState
import com.moneybook.app.AppViewModel
import com.moneybook.core.ui.PlaceholderScreen
import com.moneybook.domain.model.Household
import com.moneybook.feature.auth.AuthViewModel
import com.moneybook.feature.auth.LoginScreen
import com.moneybook.feature.home.HomeScreen
import com.moneybook.feature.home.HomeViewModel
import com.moneybook.feature.onboarding.HouseholdSetupScreen
import com.moneybook.feature.onboarding.HouseholdSetupViewModel
import com.moneybook.feature.settings.HouseholdSettingsViewModel
import com.moneybook.feature.settings.SettingsScreen
import com.moneybook.feature.statistics.StatisticsScreen
import com.moneybook.feature.transaction.AddTransactionScreen
import com.moneybook.feature.transaction.TransactionsScreen

private enum class Destination(val label: String, val symbol: String) {
    Home("홈", "⌂"), Transactions("거래", "≡"), Add("추가", "+"),
    Statistics("통계", "▥"), Settings("설정", "⚙"),
}

@Composable
fun MoneyBookNavHost(appViewModel: AppViewModel = hiltViewModel()) {
    val state by appViewModel.state.collectAsStateWithLifecycle()
    when (val value = state) {
        AppUiState.Loading -> Box(Modifier.fillMaxSize().testTag("screen_Loading")) {
            CircularProgressIndicator(Modifier.padding(24.dp))
        }
        AppUiState.ConfigurationRequired -> PlaceholderScreen(
            "Supabase 설정 필요",
            "local.properties에 SUPABASE_URL과 SUPABASE_PUBLISHABLE_KEY를 추가한 뒤 앱을 다시 빌드하세요.",
            "screen_ConfigurationRequired",
        )
        AppUiState.Unauthenticated -> AuthEntry(appViewModel::refresh)
        AppUiState.HouseholdRequired -> HouseholdEntry(appViewModel::refresh, appViewModel::signOut)
        is AppUiState.Ready -> MoneyBookMainNavigation(
            value.household, appViewModel::refresh, appViewModel::signOut,
        )
        is AppUiState.Error -> PlaceholderScreen(
            "불러오지 못했습니다", value.message, "screen_Error", "다시 시도", appViewModel::refresh,
        )
    }
}

@Composable
private fun AuthEntry(onAuthenticated: () -> Unit) {
    val viewModel: AuthViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginScreen(
        state,
        { email, password -> viewModel.signIn(email, password, onAuthenticated) },
        { email, password -> viewModel.signUp(email, password, onAuthenticated) },
    )
}

@Composable
private fun HouseholdEntry(onReady: () -> Unit, onSignOut: () -> Unit) {
    val viewModel: HouseholdSetupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    HouseholdSetupScreen(
        state,
        { viewModel.create(it, onReady) },
        { viewModel.join(it, onReady) },
        onSignOut,
    )
}

@Composable
internal fun MoneyBookMainNavigation(
    household: Household,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(bottomBar = {
        if (route != Destination.Add.name) {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = route == destination.name,
                        onClick = {
                            navController.navigate(destination.name) {
                                if (destination != Destination.Add) {
                                    popUpTo(Destination.Home.name) { saveState = true }
                                    restoreState = true
                                }
                                launchSingleTop = true
                            }
                        },
                        icon = { Text(destination.symbol, style = MaterialTheme.typography.titleLarge) },
                        label = { Text(destination.label) },
                        modifier = Modifier.testTag("nav_${destination.name}"),
                    )
                }
            }
        }
    }) { padding ->
        NavHost(navController, Destination.Home.name, Modifier.padding(padding)) {
            composable(Destination.Home.name) {
                val viewModel: HomeViewModel = hiltViewModel()
                val homeState by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(homeState, household)
            }
            composable(Destination.Transactions.name) { TransactionsScreen() }
            composable(Destination.Add.name) { AddTransactionScreen(navController::popBackStack) }
            composable(Destination.Statistics.name) { StatisticsScreen() }
            composable(Destination.Settings.name) {
                val viewModel: HouseholdSettingsViewModel = hiltViewModel()
                val settingsState by viewModel.state.collectAsStateWithLifecycle()
                SettingsScreen(household, settingsState, viewModel::createInvitation, onRefresh, onSignOut)
            }
        }
    }
}
