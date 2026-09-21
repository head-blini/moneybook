package com.moneybook.feature.transaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneybook.domain.model.RefundStatus
import com.moneybook.domain.model.Transaction
import com.moneybook.domain.model.TransactionScope
import com.moneybook.domain.model.TransactionStatus
import com.moneybook.domain.model.TransactionType
import com.moneybook.feature.home.formatKrw
import java.time.format.DateTimeFormatter

@Composable
fun TransactionsScreen(state: TransactionsUiState, viewModel: TransactionsViewModel) {
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = notice,
            actionLabel = if (state.lastDeletedId != null) "복구" else null,
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.restoreLastDeleted()
        viewModel.clearNotice()
    }
    Scaffold(
        modifier = Modifier.testTag("screen_Transactions"),
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            MonthHeader(state, viewModel)
            FilterRow(state.filter, viewModel::setFilter)
            state.error?.let {
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::retry) { Text("다시 시도") }
                    }
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.visibleTransactions.isEmpty() -> Box(
                    Modifier.fillMaxSize().testTag("transactions_empty"), contentAlignment = Alignment.Center,
                ) { Text("이 달의 거래가 없습니다.\n아래 추가 버튼으로 첫 거래를 기록해 보세요.") }
                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.visibleTransactions, key = Transaction::id) { transaction ->
                        TransactionCard(
                            transaction,
                            state.categories.firstOrNull { it.id == transaction.categoryId }?.name ?: "카테고리",
                            onClick = { viewModel.select(transaction) },
                        )
                    }
                }
            }
        }
    }
    val selected = state.selected
    when {
        state.editor != null && selected != null -> EditTransactionDialog(state, selected, viewModel)
        selected != null -> TransactionDetailDialog(state, selected, viewModel)
    }
}

@Composable
private fun MonthHeader(state: TransactionsUiState, viewModel: TransactionsViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = viewModel::previousMonth) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("거래 내역", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${state.month.year}년 ${state.month.monthValue}월", style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = viewModel::nextMonth) { Text("›", style = MaterialTheme.typography.headlineMedium) }
    }
}

@Composable
private fun FilterRow(selected: TransactionFilter, onSelected: (TransactionFilter) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransactionFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(when (filter) {
                    TransactionFilter.ALL -> "전체"
                    TransactionFilter.SHARED -> "공동"
                    TransactionFilter.PERSONAL -> "내 개인"
                }) },
                modifier = Modifier.weight(1f).testTag("filter_${filter.name}"),
            )
        }
    }
}

@Composable
private fun TransactionCard(transaction: Transaction, category: String, onClick: () -> Unit) {
    val amountColor = if (transaction.type == TransactionType.INCOME) Color(0xFF16835A) else MaterialTheme.colorScheme.error
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("transaction_${transaction.id}")) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(category.take(4), Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(transaction.merchant ?: category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${transaction.transactionAt.atZone(SEOUL).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))} · " +
                        (if (transaction.scope == TransactionScope.SHARED) "공동" else "개인"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (transaction.type == TransactionType.INCOME) "+" else "−") + formatKrw(transaction.amount),
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    transactionStatusLabel(transaction.status),
                    color = if (transaction.status == TransactionStatus.CANCELED) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun TransactionDetailDialog(state: TransactionsUiState, transaction: Transaction, viewModel: TransactionsViewModel) {
    val confirmed = state.refunds.filter { it.status == RefundStatus.CONFIRMED }.sumOf { it.amount }
    val remaining = (transaction.amount - confirmed).coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = { viewModel.select(null) },
        title = { Text(transaction.merchant ?: "거래 상세") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (transaction.type == TransactionType.EXPENSE) "지출 ${formatKrw(transaction.amount)}" else "수입 ${formatKrw(transaction.amount)}",
                    style = MaterialTheme.typography.titleLarge)
                Text("${if (transaction.scope == TransactionScope.SHARED) "공동" else "개인"} · ${state.categories.firstOrNull { it.id == transaction.categoryId }?.name.orEmpty()}")
                Text(
                    "상태: ${transactionStatusLabel(transaction.status)}",
                    color = if (transaction.status == TransactionStatus.CANCELED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                transaction.memo?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (transaction.type == TransactionType.EXPENSE) {
                    HorizontalDivider()
                    Text("환불 ${formatKrw(confirmed)} · 남은 금액 ${formatKrw(remaining)}", fontWeight = FontWeight.SemiBold)
                    state.refunds.forEach { refund ->
                        Text("${refund.refundedAt.atZone(SEOUL).toLocalDate()}  ${formatKrw(refund.amount)}  ${refundStatusLabel(refund.status)}")
                    }
                    if (remaining > 0) {
                        OutlinedTextField(
                            value = state.refundAmount,
                            onValueChange = viewModel::setRefundAmount,
                            label = { Text("부분 또는 전체 환불금액") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("refund_amount"),
                        )
                        Button(onClick = viewModel::createRefund, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("환불 등록") }
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::beginEdit) { Text("수정") } },
        dismissButton = {
            Row {
                TextButton(onClick = viewModel::deleteSelected, enabled = !state.saving) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { viewModel.select(null) }) { Text("닫기") }
            }
        },
    )
}

internal fun transactionStatusLabel(status: TransactionStatus): String = when (status) {
    TransactionStatus.PENDING -> "처리 중"
    TransactionStatus.CONFIRMED -> "확정"
    TransactionStatus.CANCELED -> "전액 환불"
}

internal fun refundStatusLabel(status: RefundStatus): String = when (status) {
    RefundStatus.PENDING -> "환불 처리 중"
    RefundStatus.CONFIRMED -> "환불 완료"
    RefundStatus.CANCELED -> "환불 취소"
}

@Composable
private fun EditTransactionDialog(state: TransactionsUiState, transaction: Transaction, viewModel: TransactionsViewModel) {
    val editor = state.editor ?: return
    AlertDialog(
        onDismissRequest = viewModel::cancelEdit,
        title = { Text("거래 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("공동/개인 범위는 변경할 수 없습니다.", style = MaterialTheme.typography.bodySmall)
                TransactionFormFields(
                    form = editor,
                    categories = state.categories,
                    cards = state.cards,
                    scopeEditable = false,
                    cardEditable = transaction.paidBy == viewModel.currentUserId(),
                    onType = viewModel::editType,
                    onScope = {},
                    onAmount = viewModel::editAmount,
                    onDate = viewModel::editDate,
                    onTime = viewModel::editTime,
                    onCategory = viewModel::editCategory,
                    onCard = viewModel::editCard,
                    onMerchant = viewModel::editMerchant,
                    onMemo = viewModel::editMemo,
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                }
                Button(
                    onClick = viewModel::saveEdit,
                    enabled = !state.saving,
                    modifier = Modifier.testTag("transaction_edit_save"),
                ) { Text(if (state.saving) "저장 중…" else "저장") }
            }
        },
        dismissButton = { TextButton(onClick = viewModel::cancelEdit) { Text("취소") } },
    )
}
