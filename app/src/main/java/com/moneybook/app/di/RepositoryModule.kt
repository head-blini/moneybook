package com.moneybook.app.di

import com.moneybook.data.repository.SupabaseAuthRepository
import com.moneybook.data.repository.SupabaseHouseholdRepository
import com.moneybook.domain.repository.AuthRepository
import com.moneybook.domain.repository.HouseholdRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindAuthRepository(implementation: SupabaseAuthRepository): AuthRepository

    @Binds @Singleton
    abstract fun bindHouseholdRepository(implementation: SupabaseHouseholdRepository): HouseholdRepository
}
