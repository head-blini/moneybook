package com.moneybook.data.repository

import com.moneybook.core.result.AppResult
import com.moneybook.data.remote.supabase.HouseholdDto
import com.moneybook.data.remote.supabase.HouseholdMemberDto
import com.moneybook.data.remote.supabase.SupabaseProvider
import com.moneybook.domain.model.Household
import com.moneybook.domain.model.HouseholdMember
import com.moneybook.domain.repository.AuthRepository
import com.moneybook.domain.repository.HouseholdRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException

@Singleton
class SupabaseHouseholdRepository @Inject constructor(
    private val provider: SupabaseProvider,
    private val authRepository: AuthRepository,
) : HouseholdRepository {
    override suspend fun currentHousehold(): AppResult<Household?> = try {
        val userId = authRepository.currentUserId() ?: return AppResult.Success(null)
        val membership = provider.client.from("household_members").select {
            filter { eq("user_id", userId) }
        }.decodeSingleOrNull<HouseholdMemberDto>() ?: return AppResult.Success(null)
        AppResult.Success(loadHousehold(membership.householdId))
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        AppResult.Error("가구 정보를 불러오지 못했습니다. 다시 시도해 주세요.")
    }

    override suspend fun createHousehold(name: String): AppResult<Household> = runOperation {
        val id = provider.client.postgrest.rpc(
            function = "create_household",
            parameters = buildJsonObject { put("name", name.trim()) },
        ).decodeAs<String>()
        loadHousehold(id)
    }

    override suspend fun createInvitation(): AppResult<String> = runOperation {
        provider.client.postgrest.rpc("create_invitation").decodeAs<String>()
    }

    override suspend fun joinHousehold(code: String): AppResult<Household> = runOperation {
        val id = provider.client.postgrest.rpc(
            function = "join_household",
            parameters = buildJsonObject { put("code", code.trim().uppercase()) },
        ).decodeAs<String>()
        loadHousehold(id)
    }

    private suspend fun loadHousehold(id: String): Household {
        val household = provider.client.from("households").select {
            filter { eq("id", id) }
        }.decodeSingle<HouseholdDto>()
        val members = provider.client.from("household_members").select {
            filter { eq("household_id", id) }
        }.decodeList<HouseholdMemberDto>()
        return Household(
            id = household.id,
            name = household.name,
            members = members.map { HouseholdMember(it.userId, it.role) },
        )
    }

    private suspend fun <T> runOperation(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        AppResult.Error("가구 요청을 완료하지 못했습니다. 입력을 확인하고 다시 시도해 주세요.")
    }
}
