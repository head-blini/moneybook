package com.moneybook.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.moneybook.domain.model.Household
import java.text.NumberFormat
import java.util.Locale

internal fun formatKrw(amount: Long): String = "₩" + NumberFormat.getIntegerInstance(Locale.KOREA).format(amount)

@Composable
fun HomeScreen(state: HomeUiState, household: Household? = null, onRetry: () -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize().testTag("screen_Home"),
        contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("MoneyBook · ${state.month.year}년 ${state.month.monthValue}월", style = MaterialTheme.typography.headlineMedium)
            household?.let { Text("${it.name} · 구성원 ${it.members.size}/2", style = MaterialTheme.typography.labelLarge) }
        }
        when {
            state.loading -> item { CircularProgressIndicator(Modifier.testTag("home_loading")) }
            state.error != null -> item {
                Column {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("다시 시도") }
                }
            }
            else -> {
                state.summary?.let { summary -> item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("이번 달 수입  ${formatKrw(summary.income)}")
                            Text("이번 달 지출  ${formatKrw(summary.expense)}")
                            Text("잔액  ${formatKrw(summary.balance)}", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                } }
                item { Text("최근 거래", style = MaterialTheme.typography.titleLarge) }
                if (state.empty) item { Text("이번 달 거래 내역이 없습니다.") }
                items(state.recentTransactions, key = { it.id }) { transaction ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(transaction.title, style = MaterialTheme.typography.titleMedium)
                        transaction.categoryName?.let { category ->
                            Text(category, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${if (transaction.income) "+" else "-"}${formatKrw(transaction.amount)}",
                            style = MaterialTheme.typography.titleLarge)
                        if (transaction.originalAmount > transaction.amount) {
                            Text("원금 ${formatKrw(transaction.originalAmount)} · 환불 ${formatKrw(transaction.originalAmount - transaction.amount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
