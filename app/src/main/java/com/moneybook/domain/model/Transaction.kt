package com.moneybook.domain.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class TransactionType { EXPENSE, INCOME }
enum class TransactionScope { SHARED, PERSONAL }
enum class TransactionStatus { PENDING, CONFIRMED, CANCELED }
enum class RefundStatus { PENDING, CONFIRMED, CANCELED }

data class Category(
    val id: String,
    val name: String,
    val type: TransactionType,
    val isActive: Boolean,
)

data class Card(
    val id: String,
    val ownerUserId: String,
    val displayName: String,
    val lastFour: String?,
    val isActive: Boolean,
)

data class Transaction(
    val id: String,
    val householdId: String,
    val createdBy: String,
    val paidBy: String,
    val type: TransactionType,
    val scope: TransactionScope,
    val amount: Long,
    val categoryId: String,
    val cardId: String?,
    val merchant: String?,
    val memo: String?,
    val transactionAt: Instant,
    val status: TransactionStatus,
)

data class TransactionRefund(
    val id: String,
    val transactionId: String,
    val amount: Long,
    val status: RefundStatus,
    val refundedAt: Instant,
)

data class MonthlySummary(
    val sharedIncome: Long,
    val sharedExpense: Long,
    val personalIncome: Long,
    val personalExpense: Long,
) {
    val income: Long get() = sharedIncome + personalIncome
    val expense: Long get() = sharedExpense + personalExpense
    val balance: Long get() = income - expense
}

fun seoulMonthBounds(month: YearMonth): Pair<Instant, Instant> {
    val zone = ZoneId.of("Asia/Seoul")
    return month.atDay(1).atStartOfDay(zone).toInstant() to
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
}

data class TransactionDraft(
    val type: TransactionType,
    val scope: TransactionScope,
    val amount: Long,
    val categoryId: String,
    val cardId: String?,
    val merchant: String?,
    val memo: String?,
    val transactionAt: Instant,
)
