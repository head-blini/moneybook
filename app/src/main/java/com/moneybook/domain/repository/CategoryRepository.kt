package com.moneybook.domain.repository

import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.Category

interface CategoryRepository {
    suspend fun getActiveCategories(): AppResult<List<Category>>
}
