package com.moneybook.data.remote.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class HouseholdDto(val id: String, val name: String)

@Serializable
internal data class HouseholdMemberDto(
    @SerialName("household_id") val householdId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
)
