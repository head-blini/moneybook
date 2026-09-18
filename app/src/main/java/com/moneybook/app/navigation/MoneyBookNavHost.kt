package com.moneybook.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.moneybook.feature.auth.LoginScreen
import com.moneybook.feature.onboarding.*
import com.moneybook.feature.home.*
import com.moneybook.feature.transaction.*
import com.moneybook.feature.statistics.StatisticsScreen
import com.moneybook.feature.settings.SettingsScreen

internal enum class Destination(val label: String, val symbol: String = "") {
    Login("로그인"), HouseholdSetup("가구 설정"), NotificationPermission("알림 연결"),
    Home("홈", "⌂"), Transactions("거래", "≡"), Add("추가", "+"),
    Statistics("통계", "▥"), Settings("설정", "⚙"),
}
private val mainDestinations = listOf(Destination.Home, Destination.Transactions,
    Destination.Add, Destination.Statistics, Destination.Settings)

@Composable
fun MoneyBookNavHost() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(bottomBar = {
        if (mainDestinations.any { it.name == route } && route != Destination.Add.name) {
            NavigationBar {
                mainDestinations.forEach { destination ->
                    NavigationBarItem(selected = route == destination.name, onClick = {
                        navController.navigate(destination.name) {
                            if (destination != Destination.Add) {
                                popUpTo(Destination.Home.name) { saveState = true }
                                restoreState = true
                            }
                            launchSingleTop = true
                        }
                    }, icon = { Text(destination.symbol, style = MaterialTheme.typography.titleLarge) },
                        label = { Text(destination.label) }, modifier = Modifier.testTag("nav_${destination.name}"))
                }
            }
        }
    }) { padding ->
        // PHASE_1_DEV: replace local entry routing with session/household state in Phase 2.
        NavHost(navController, startDestination = Destination.Login.name, modifier = Modifier.padding(padding)) {
            composable(Destination.Login.name) {
                LoginScreen { navController.navigate(Destination.HouseholdSetup.name) { launchSingleTop = true } }
            }
            composable(Destination.HouseholdSetup.name) {
                HouseholdSetupScreen { navController.navigate(Destination.NotificationPermission.name) { launchSingleTop = true } }
            }
            composable(Destination.NotificationPermission.name) {
                NotificationPermissionScreen {
                    navController.navigate(Destination.Home.name) {
                        popUpTo(Destination.Login.name) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            composable(Destination.Home.name) {
                val viewModel: HomeViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(state)
            }
            composable(Destination.Transactions.name) { TransactionsScreen() }
            composable(Destination.Add.name) { AddTransactionScreen { navController.popBackStack() } }
            composable(Destination.Statistics.name) { StatisticsScreen() }
            composable(Destination.Settings.name) { SettingsScreen() }
        }
    }
}
