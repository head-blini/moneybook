package com.moneybook.data.repository

import android.util.Log
import com.moneybook.core.result.AppResult
import com.moneybook.data.remote.supabase.CardDto
import com.moneybook.data.remote.supabase.SupabaseProvider
import com.moneybook.data.remote.supabase.TransactionDto
import com.moneybook.data.remote.supabase.TransactionInsertDto
import com.moneybook.data.remote.supabase.MonthlySummaryDto
import com.moneybook.data.remote.supabase.TransactionRefundDto
import com.moneybook.data.remote.supabase.TransactionUpdateDto
import com.moneybook.domain.model.Card
import com.moneybook.domain.model.RefundStatus
import com.moneybook.domain.model.MonthlySummary
import com.moneybook.domain.model.Transaction
import com.moneybook.domain.model.TransactionDraft
import com.moneybook.domain.model.TransactionRefund
import com.moneybook.domain.model.TransactionScope
import com.moneybook.domain.model.TransactionStatus
import com.moneybook.domain.model.TransactionType
import com.moneybook.domain.repository.AuthRepository
import com.moneybook.domain.repository.HouseholdRepository
import com.moneybook.domain.repository.TransactionRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class SupabaseTransactionRepository @Inject constructor(
    private val provider: SupabaseProvider,
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
) : TransactionRepository {
    override suspend fun getMonthlySummary(month: YearMonth): AppResult<MonthlySummary> = request(
        "월간 요약을 불러오지 못했습니다. 다시 시도해 주세요.",
    ) {
        val dto = provider.client.postgrest.rpc(
            "get_monthly_summary",
            buildJsonObject { put("month_start", month.atDay(1).toString()) },
        ).decodeList<MonthlySummaryDto>().single()
        MonthlySummary(dto.sharedIncome, dto.sharedExpense, dto.personalIncome, dto.personalExpense)
    }

    override suspend fun getRefundsForTransactions(ids: List<String>): AppResult<List<TransactionRefund>> = request(
        "환불 내역을 불러오지 못했습니다. 다시 시도해 주세요.",
    ) {
        if (ids.isEmpty()) emptyList() else ids.distinct().chunked(80).flatMap { batch ->
            provider.client.from("transaction_refunds").select {
                filter { isIn("transaction_id", batch) }
            }.decodeList<TransactionRefundDto>().map(TransactionRefundDto::toDomain)
        }
    }

    override suspend fun getTransactions(month: YearMonth): AppResult<List<Transaction>> = request(
        "거래 내역을 불러오지 못했습니다. 다시 시도해 주세요.",
    ) {
        val zone = ZoneId.of("Asia/Seoul")
        val start = month.atDay(1).atStartOfDay(zone).toInstant()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
        provider.client.from("transactions").select {
            filter {
                gte("transaction_at", start.toString())
                lt("transaction_at", end.toString())
            }
            order("transaction_at", Order.DESCENDING)
        }.decodeList<TransactionDto>().map(TransactionDto::toDomain)
    }

    override suspend fun getCards(): AppResult<List<Card>> = request(
        "카드 목록을 불러오지 못했습니다.",
    ) {
        provider.client.from("cards").select {
            filter { eq("is_active", true) }
            order("display_name", Order.ASCENDING)
        }.decodeList<CardDto>().map { Card(it.id, it.ownerUserId, it.displayName, it.lastFour, it.isActive) }
    }

    override suspend fun createTransaction(draft: TransactionDraft): AppResult<Transaction> = request(
        "거래를 저장하지 못했습니다. 입력을 확인해 주세요.",
    ) {
        val userId = requireNotNull(authRepository.currentUserId())
        val household = when (val result = householdRepository.currentHousehold()) {
            is AppResult.Success -> requireNotNull(result.value)
            is AppResult.Error -> error(result.message)
        }
        val transactionId = UUID.randomUUID().toString()
        val merchant = draft.merchant.clean()
        val memo = draft.memo.clean()
        provider.client.from("transactions").insert(
            TransactionInsertDto(
                id = transactionId,
                householdId = household.id,
                createdBy = userId,
                paidBy = userId,
                type = draft.type.name,
                scope = draft.scope.name,
                amount = draft.amount,
                categoryId = draft.categoryId,
                cardId = draft.cardId,
                merchant = merchant,
                memo = memo,
                transactionAt = draft.transactionAt.toString(),
                source = "MANUAL",
            ),
        )
        Transaction(
            id = transactionId,
            householdId = household.id,
            createdBy = userId,
            paidBy = userId,
            type = draft.type,
            scope = draft.scope,
            amount = draft.amount,
            categoryId = draft.categoryId,
            cardId = draft.cardId,
            merchant = merchant,
            memo = memo,
            transactionAt = draft.transactionAt,
            status = TransactionStatus.CONFIRMED,
        )
    }

    override suspend fun updateTransaction(id: String, draft: TransactionDraft): AppResult<Transaction> = request(
        "거래를 수정하지 못했습니다. 입력을 확인해 주세요.",
    ) {
        provider.client.from("transactions").update(
            TransactionUpdateDto(
                type = draft.type.name,
                amount = draft.amount,
                categoryId = draft.categoryId,
                cardId = draft.cardId,
                merchant = draft.merchant.clean(),
                memo = draft.memo.clean(),
                transactionAt = draft.transactionAt.toString(),
            ),
        ) {
            filter { eq("id", id) }
            select()
        }.decodeList<TransactionDto>().singleOrNull()?.toDomain()
            ?: error("Updated transaction is not visible")
    }

    override suspend fun softDeleteTransaction(id: String): AppResult<Unit> = rpcUnit(
        "soft_delete_transaction", "거래를 삭제하지 못했습니다.", id,
    )

    override suspend fun restoreTransaction(id: String): AppResult<Unit> = rpcUnit(
        "restore_transaction", "거래를 복구하지 못했습니다.", id,
    )

    override suspend fun getRefunds(transactionId: String): AppResult<List<TransactionRefund>> = request(
        "환불 이력을 불러오지 못했습니다.",
    ) {
        provider.client.from("transaction_refunds").select {
            filter { eq("transaction_id", transactionId) }
            order("refunded_at", Order.DESCENDING)
        }.decodeList<TransactionRefundDto>().map(TransactionRefundDto::toDomain)
    }

    override suspend fun createRefund(transactionId: String, amount: Long): AppResult<Unit> = try {
        provider.client.postgrest.rpc(
            "create_transaction_refund",
            buildJsonObject {
                put("transaction_id", transactionId)
                put("amount", amount)
                put("idempotency_key", UUID.randomUUID().toString())
                put("refunded_at", Instant.now().toString())
                put("source", "MANUAL")
            },
        )
        AppResult.Success(Unit)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        AppResult.Error("환불을 등록하지 못했습니다. 환불 가능 금액을 확인해 주세요.")
    }

    override fun currentUserId(): String? = authRepository.currentUserId()

    private suspend fun rpcUnit(function: String, message: String, id: String): AppResult<Unit> = try {
        provider.client.postgrest.rpc(function, buildJsonObject { put("transaction_id", id) })
        AppResult.Success(Unit)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        AppResult.Error(message)
    }

    private suspend fun <T> request(message: String, block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        when (error) {
            is PostgrestRestException -> Log.e(
                TAG,
                "PostgREST status=${error.statusCode} code=${error.code} " +
                    "error=${error.error} hint=${error.hint} details=${error.details}",
            )
            else -> Log.e(TAG, "Transaction request failed: ${error::class.simpleName}")
        }
        AppResult.Error(message)
    }

    private companion object {
        const val TAG = "MoneyBookTransaction"
    }
}

private fun TransactionDto.toDomain() = Transaction(
    id = id,
    householdId = householdId,
    createdBy = createdBy,
    paidBy = paidBy,
    type = TransactionType.valueOf(type),
    scope = TransactionScope.valueOf(scope),
    amount = amount,
    categoryId = categoryId,
    cardId = cardId,
    merchant = merchant,
    memo = memo,
    transactionAt = Instant.parse(transactionAt),
    status = TransactionStatus.valueOf(status),
)

private fun TransactionRefundDto.toDomain() = TransactionRefund(
    id, transactionId, amount, RefundStatus.valueOf(status), Instant.parse(refundedAt),
)

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
