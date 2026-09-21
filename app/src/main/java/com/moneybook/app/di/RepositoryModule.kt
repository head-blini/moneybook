package com.moneybook.app.di

import com.moneybook.data.repository.SupabaseAuthRepository
import com.moneybook.data.repository.SupabaseCategoryRepository
import com.moneybook.data.repository.SupabaseHouseholdRepository
import com.moneybook.data.repository.SupabaseTransactionRepository
import com.moneybook.domain.repository.AuthRepository
import com.moneybook.domain.repository.CategoryRepository
import com.moneybook.domain.repository.HouseholdRepository
import com.moneybook.domain.repository.TransactionRepository
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

    @Binds @Singleton
    abstract fun bindCategoryRepository(implementation: SupabaseCategoryRepository): CategoryRepository

    @Binds @Singleton
    abstract fun bindTransactionRepository(implementation: SupabaseTransactionRepository): TransactionRepository
}
