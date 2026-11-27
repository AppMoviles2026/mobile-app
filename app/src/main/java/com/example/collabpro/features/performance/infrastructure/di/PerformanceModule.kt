package com.example.collabpro.features.performance.infrastructure.di

import com.example.collabpro.core.infrastructure.network.ApiExecutor
import com.example.collabpro.features.performance.domain.repositories.PerformanceRepository
import com.example.collabpro.features.performance.infrastructure.remote.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal object PerformanceModule {
    @Provides
    fun repository(retrofit: Retrofit, executor: ApiExecutor): PerformanceRepository =
        RemotePerformance(retrofit.create(PerformanceApi::class.java), executor)
}
