package com.example.collabpro.features.billing.infrastructure.di

import com.example.collabpro.core.infrastructure.network.ApiExecutor
import com.example.collabpro.features.billing.domain.repositories.BillingRepository
import com.example.collabpro.features.billing.infrastructure.remote.BillingApi
import com.example.collabpro.features.billing.infrastructure.remote.RemoteBilling
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal object BillingModule {
    @Provides
    fun provide(retrofit: Retrofit, executor: ApiExecutor): BillingRepository =
        RemoteBilling(retrofit.create(BillingApi::class.java), executor)
}
