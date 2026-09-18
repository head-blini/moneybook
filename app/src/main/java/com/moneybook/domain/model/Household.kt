package com.moneybook.domain.model

data class Household(
    val id: String,
    val name: String,
    val members: List<HouseholdMember>,
)

data class HouseholdMember(
    val userId: String,
    val role: String,
)
