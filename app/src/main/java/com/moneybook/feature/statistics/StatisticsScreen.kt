package com.moneybook.feature.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.moneybook.feature.home.formatKrw
import com.moneybook.feature.transaction.SEOUL
import java.time.YearMonth

@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().testTag("screen_Statistics"),
        contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("월간 통계", style = MaterialTheme.typography.headlineLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onPreviousMonth, modifier = Modifier.testTag("statistics_previous")) { Text("이전 달") }
                Text("${state.month.year}년 ${state.month.monthValue}월", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onNextMonth, enabled = state.month < YearMonth.now(SEOUL),
                    modifier = Modifier.testTag("statistics_next")) { Text("다음 달") }
            }
        }
        when {
            state.loading -> item { CircularProgressIndicator(Modifier.testTag("statistics_loading")) }
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
                            Text("수입  ${formatKrw(summary.income)}")
                            Text("지출  ${formatKrw(summary.expense)}")
                            Text("잔액  ${formatKrw(summary.balance)}", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                } }
                if (state.empty) item { Text("이 달의 거래 내역이 없습니다.") }
                item { Text("카테고리별 지출", style = MaterialTheme.typography.titleLarge) }
                if (state.categories.isEmpty()) item { Text("지출 내역 없음") }
                items(state.categories, key = { it.name }) { category ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(category.name, style = MaterialTheme.typography.titleMedium)
                        Text("${formatKrw(category.amount)} · ${category.percentage}%")
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
