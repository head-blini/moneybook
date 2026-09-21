package com.moneybook.feature.transaction

import com.moneybook.domain.model.Card
import com.moneybook.domain.model.Category
import com.moneybook.domain.model.Transaction
import com.moneybook.domain.model.TransactionDraft
import com.moneybook.domain.model.TransactionScope
import com.moneybook.domain.model.TransactionType
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TransactionFormState(
    val type: TransactionType = TransactionType.EXPENSE,
    val scope: TransactionScope = TransactionScope.SHARED,
    val amount: String = "",
    val date: String = LocalDate.now(SEOUL).toString(),
    val time: String = LocalTime.now(SEOUL).format(DateTimeFormatter.ofPattern("HH:mm")),
    val categoryId: String? = null,
    val cardId: String? = null,
    val merchant: String = "",
    val memo: String = "",
)

data class TransactionFormOptions(
    val categories: List<Category> = emptyList(),
    val cards: List<Card> = emptyList(),
)

internal val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

internal fun TransactionFormState.validateAndBuild(): FormResult {
    val parsedAmount = amount.filter(Char::isDigit).toLongOrNull()
        ?: return FormResult.Invalid("금액을 숫자로 입력해 주세요.")
    if (parsedAmount <= 0) return FormResult.Invalid("금액은 1원 이상이어야 합니다.")
    val category = categoryId ?: return FormResult.Invalid("카테고리를 선택해 주세요.")
    if (merchant.trim().length > 120) return FormResult.Invalid("거래명은 120자 이하로 입력해 주세요.")
    if (memo.trim().length > 500) return FormResult.Invalid("메모는 500자 이하로 입력해 주세요.")
    val instant = try {
        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(SEOUL).toInstant()
    } catch (_: DateTimeException) {
        return FormResult.Invalid("날짜는 YYYY-MM-DD, 시간은 HH:mm 형식으로 입력해 주세요.")
    }
    return FormResult.Valid(
        TransactionDraft(
            type = type,
            scope = scope,
            amount = parsedAmount,
            categoryId = category,
            cardId = cardId.takeIf { type == TransactionType.EXPENSE },
            merchant = merchant,
            memo = memo,
            transactionAt = instant,
        ),
    )
}

internal sealed interface FormResult {
    data class Valid(val draft: TransactionDraft) : FormResult
    data class Invalid(val message: String) : FormResult
}

internal fun Transaction.toFormState() = TransactionFormState(
    type = type,
    scope = scope,
    amount = amount.toString(),
    date = transactionAt.atZone(SEOUL).toLocalDate().toString(),
    time = transactionAt.atZone(SEOUL).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
    categoryId = categoryId,
    cardId = cardId,
    merchant = merchant.orEmpty(),
    memo = memo.orEmpty(),
)
