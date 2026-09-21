package com.moneybook.data.repository

import com.moneybook.core.result.AppResult
import com.moneybook.data.remote.supabase.CategoryDto
import com.moneybook.data.remote.supabase.SupabaseProvider
import com.moneybook.domain.model.Category
import com.moneybook.domain.model.TransactionType
import com.moneybook.domain.repository.CategoryRepository
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class SupabaseCategoryRepository @Inject constructor(
    private val provider: SupabaseProvider,
) : CategoryRepository {
    override suspend fun getActiveCategories(): AppResult<List<Category>> = try {
        val categories = provider.client.from("categories").select {
            filter { eq("is_active", true) }
            order("name", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
        }.decodeList<CategoryDto>().map { dto ->
            Category(dto.id, dto.name, TransactionType.valueOf(dto.transactionType), dto.isActive)
        }
        AppResult.Success(categories)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        AppResult.Error("카테고리를 불러오지 못했습니다. 다시 시도해 주세요.")
    }
}
