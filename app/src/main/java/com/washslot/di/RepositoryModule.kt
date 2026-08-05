package com.washslot.di

import com.washslot.data.repository.AuthRepositoryImpl
import com.washslot.data.repository.LaundryRepositoryImpl
import com.washslot.data.repository.ReportRepositoryImpl
import com.washslot.domain.repository.AuthRepository
import com.washslot.domain.repository.LaundryRepository
import com.washslot.domain.repository.ReportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindLaundryRepository(
        laundryRepositoryImpl: LaundryRepositoryImpl
    ): LaundryRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(
        reportRepositoryImpl: ReportRepositoryImpl
    ): ReportRepository
}
