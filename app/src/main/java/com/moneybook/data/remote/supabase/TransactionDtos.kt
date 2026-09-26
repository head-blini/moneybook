package com.moneybook.data.remote.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    @SerialName("transaction_type") val transactionType: String,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class CardDto(
    val id: String,
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("last_four") val lastFour: String? = null,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class TransactionDto(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("paid_by") val paidBy: String,
    val type: String,
    val scope: String,
    val amount: Long,
    @SerialName("category_id") val categoryId: String,
    @SerialName("card_id") val cardId: String? = null,
    val merchant: String? = null,
    val memo: String? = null,
    @SerialName("transaction_at") val transactionAt: String,
    val status: String,
)

@Serializable
data class TransactionInsertDto(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("paid_by") val paidBy: String,
    val type: String,
    val scope: String,
    val amount: Long,
    @SerialName("category_id") val categoryId: String,
    @SerialName("card_id") val cardId: String?,
    val merchant: String?,
    val memo: String?,
    @SerialName("transaction_at") val transactionAt: String,
    val source: String,
)

@Serializable
data class TransactionUpdateDto(
    val type: String,
    val amount: Long,
    @SerialName("category_id") val categoryId: String,
    @SerialName("card_id") val cardId: String?,
    val merchant: String?,
    val memo: String?,
    @SerialName("transaction_at") val transactionAt: String,
)

@Serializable
data class TransactionRefundDto(
    val id: String,
    @SerialName("transaction_id") val transactionId: String,
    val amount: Long,
    val status: String,
    @SerialName("refunded_at") val refundedAt: String,
)

@Serializable
data class MonthlySummaryDto(
    @SerialName("shared_income") val sharedIncome: Long,
    @SerialName("shared_expense") val sharedExpense: Long,
    @SerialName("personal_income") val personalIncome: Long,
    @SerialName("personal_expense") val personalExpense: Long,
)
