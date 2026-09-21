package com.moneybook.data.repository

import com.moneybook.core.result.AppResult
import com.moneybook.data.remote.supabase.SupabaseProvider
import com.moneybook.domain.repository.AuthRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val provider: SupabaseProvider,
) : AuthRepository {
    override val isConfigured: Boolean
        get() = provider.isConfigured

    override suspend fun hasSession(): Boolean {
        if (!provider.isConfigured) return false
        return provider.client.auth.run {
            awaitInitialization()
            sessionStatus.value.hasUsableSession()
        }
    }

    override suspend fun signUp(email: String, password: String): AppResult<Unit> = runRequest {
        provider.client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signIn(email: String, password: String): AppResult<Unit> = runRequest {
        provider.client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signOut(): AppResult<Unit> = runRequest { provider.client.auth.signOut() }

    override fun currentUserId(): String? =
        if (provider.isConfigured) provider.client.auth.currentUserOrNull()?.id else null

    private suspend fun runRequest(block: suspend () -> Unit): AppResult<Unit> {
        if (!provider.isConfigured) return AppResult.Error("Supabase 설정이 필요합니다.")
        return try {
            block()
            AppResult.Success(Unit)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            AppResult.Error("인증 요청을 완료하지 못했습니다. 입력과 네트워크를 확인해 주세요.")
        }
    }
}

internal fun SessionStatus.hasUsableSession(): Boolean = this is SessionStatus.Authenticated
