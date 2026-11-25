package com.example.collabpro.features.collaboration.infrastructure.di

import com.example.collabpro.core.infrastructure.network.ApiExecutor
import com.example.collabpro.features.collaboration.domain.repositories.CollaborationRepository
import com.example.collabpro.features.collaboration.infrastructure.remote.CollaborationApi
import com.example.collabpro.features.collaboration.infrastructure.remote.RemoteCollaborations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal object CollaborationModule {
    @Provides
    fun collaborations(retrofit: Retrofit, executor: ApiExecutor): CollaborationRepository =
        RemoteCollaborations(retrofit.create(CollaborationApi::class.java), executor)
}
