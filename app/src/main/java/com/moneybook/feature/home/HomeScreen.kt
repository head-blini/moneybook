package com.moneybook.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moneybook.core.ui.MoneyBookTheme
import java.text.NumberFormat
import java.util.Locale

internal fun formatKrw(amount: Long): String = "₩" + NumberFormat.getIntegerInstance(Locale.KOREA).format(amount)

@Composable
fun HomeScreen(state: HomeUiState) {
    LazyColumn(Modifier.fillMaxSize().testTag("screen_Home"),
        contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("MoneyBook · ${state.month}", style = MaterialTheme.typography.headlineMedium)
            Text("개발용 예시 데이터", style = MaterialTheme.typography.labelLarge)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("총 지출", style = MaterialTheme.typography.titleMedium)
                    Text(formatKrw(state.totalSpending), style = MaterialTheme.typography.headlineLarge)
                }
            }
        }
        item {
            Text("공동 지출", style = MaterialTheme.typography.titleMedium)
            Text("${formatKrw(state.sharedSpending)} / ${formatKrw(state.sharedBudget)}",
                style = MaterialTheme.typography.titleLarge)
        }
        item { Text("최근 거래", style = MaterialTheme.typography.titleLarge) }
        items(state.recentTransactions) { transaction ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(transaction.merchant, style = MaterialTheme.typography.titleMedium)
                Text("-${formatKrw(transaction.amount)}", style = MaterialTheme.typography.titleLarge)
                Text(transaction.detail, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeLightPreview() { MoneyBookTheme(dynamicColor = false) { HomeScreen(HomeUiState()) } }

@Preview(showBackground = true)
@Composable
private fun HomeDarkPreview() { MoneyBookTheme(darkTheme = true, dynamicColor = false) { HomeScreen(HomeUiState()) } }
