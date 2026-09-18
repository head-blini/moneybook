package com.moneybook.domain.repository

import com.moneybook.core.result.AppResult

interface AuthRepository {
    suspend fun hasSession(): Boolean
    suspend fun signUp(email: String, password: String): AppResult<Unit>
    suspend fun signIn(email: String, password: String): AppResult<Unit>
    suspend fun signOut(): AppResult<Unit>
    fun currentUserId(): String?
}
