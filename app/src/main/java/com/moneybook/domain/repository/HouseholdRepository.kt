package com.moneybook.domain.repository

import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.Household

interface HouseholdRepository {
    suspend fun currentHousehold(): AppResult<Household?>
    suspend fun createHousehold(name: String): AppResult<Household>
    suspend fun createInvitation(): AppResult<String>
    suspend fun joinHousehold(code: String): AppResult<Household>
}
