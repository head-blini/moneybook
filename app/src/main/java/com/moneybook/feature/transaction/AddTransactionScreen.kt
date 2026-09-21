package com.moneybook.feature.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneybook.domain.model.Card
import com.moneybook.domain.model.Category
import com.moneybook.domain.model.TransactionScope
import com.moneybook.domain.model.TransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    state: AddTransactionUiState,
    onClose: () -> Unit,
    onType: (TransactionType) -> Unit,
    onScope: (TransactionScope) -> Unit,
    onAmount: (String) -> Unit,
    onDate: (String) -> Unit,
    onTime: (String) -> Unit,
    onCategory: (String) -> Unit,
    onCard: (String?) -> Unit,
    onMerchant: (String) -> Unit,
    onMemo: (String) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag("screen_AddTransaction"),
        topBar = { TopAppBar(title = { Text("빠른 거래 입력") }, navigationIcon = {
            OutlinedButton(onClick = onClose, modifier = Modifier.padding(start = 8.dp)) { Text("닫기") }
        }) },
    ) { padding ->
        if (state.loading) {
            CircularProgressIndicator(Modifier.padding(padding).padding(32.dp))
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("새 거래", style = MaterialTheme.typography.headlineSmall)
                        TransactionFormFields(
                            form = state.form,
                            categories = state.options.categories,
                            cards = state.options.cards,
                            scopeEditable = true,
                            cardEditable = true,
                            onType = onType,
                            onScope = onScope,
                            onAmount = onAmount,
                            onDate = onDate,
                            onTime = onTime,
                            onCategory = onCategory,
                            onCard = onCard,
                            onMerchant = onMerchant,
                            onMemo = onMemo,
                        )
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = onSave,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().testTag("transaction_save"),
                    contentPadding = PaddingValues(16.dp),
                ) { Text(if (state.saving) "저장 중…" else "거래 저장") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionFormFields(
    form: TransactionFormState,
    categories: List<Category>,
    cards: List<Card>,
    scopeEditable: Boolean,
    cardEditable: Boolean,
    onType: (TransactionType) -> Unit,
    onScope: (TransactionScope) -> Unit,
    onAmount: (String) -> Unit,
    onDate: (String) -> Unit,
    onTime: (String) -> Unit,
    onCategory: (String) -> Unit,
    onCard: (String?) -> Unit,
    onMerchant: (String) -> Unit,
    onMemo: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransactionType.entries.forEach { value ->
            FilterChip(
                selected = form.type == value,
                onClick = { onType(value) },
                label = { Text(if (value == TransactionType.EXPENSE) "지출" else "수입") },
                modifier = Modifier.weight(1f).testTag("type_${value.name}"),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransactionScope.entries.forEach { value ->
            FilterChip(
                selected = form.scope == value,
                onClick = { if (scopeEditable) onScope(value) },
                enabled = scopeEditable,
                label = { Text(if (value == TransactionScope.SHARED) "공동" else "개인") },
                modifier = Modifier.weight(1f).testTag("scope_${value.name}"),
            )
        }
    }
    OutlinedTextField(
        value = form.amount,
        onValueChange = onAmount,
        label = { Text("금액 (원)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("transaction_amount"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(form.date, onDate, label = { Text("날짜") }, singleLine = true, modifier = Modifier.weight(1.4f))
        OutlinedTextField(form.time, onTime, label = { Text("시간") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    SelectionField(
        label = "카테고리",
        selectedLabel = categories.firstOrNull { it.id == form.categoryId }?.name.orEmpty(),
        options = categories.filter { it.type == form.type }.map { it.id to it.name },
        onSelected = { onCategory(requireNotNull(it)) },
        modifier = Modifier.testTag("transaction_category"),
    )
    if (form.type == TransactionType.EXPENSE) {
        SelectionField(
            label = "카드 (선택)",
            selectedLabel = cards.firstOrNull { it.id == form.cardId }?.let {
                if (it.lastFour == null) it.displayName else "${it.displayName} · ${it.lastFour}"
            }.orEmpty(),
            options = listOf<Pair<String?, String>>(null to "카드 없음") + cards.map {
                it.id to if (it.lastFour == null) it.displayName else "${it.displayName} · ${it.lastFour}"
            },
            onSelected = onCard,
            enabled = cardEditable,
        )
    }
    OutlinedTextField(form.merchant, onMerchant, label = { Text("가맹점 또는 거래명") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("transaction_merchant"))
    OutlinedTextField(form.memo, onMemo, label = { Text("메모") }, minLines = 2,
        modifier = Modifier.fillMaxWidth().testTag("transaction_memo"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectionField(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelected(value) })
            }
        }
    }
}
