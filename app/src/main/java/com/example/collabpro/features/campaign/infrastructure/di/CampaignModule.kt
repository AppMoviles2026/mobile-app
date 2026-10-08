package com.example.collabpro.features.campaign.infrastructure.di

import com.example.collabpro.core.infrastructure.network.ApiExecutor
import com.example.collabpro.features.campaign.application.usecases.*
import com.example.collabpro.features.campaign.domain.repositories.*
import com.example.collabpro.features.campaign.infrastructure.*
import com.example.collabpro.features.campaign.infrastructure.remote.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CampaignModule {
    @Provides @Singleton fun campaignsApi(retrofit: Retrofit): CampaignApi = retrofit.create(CampaignApi::class.java)
    @Provides @Singleton fun applicationsApi(retrofit: Retrofit): ApplicationApi = retrofit.create(ApplicationApi::class.java)
    @Provides @Singleton fun campaigns(api: CampaignApi, executor: ApiExecutor): CampaignRepository = RemoteCampaignRepository(api, executor)
    @Provides @Singleton fun applications(api: ApplicationApi, executor: ApiExecutor): ApplicationRepository = RemoteApplicationRepository(api, executor)
    @Provides @Singleton fun campaignUseCases(repository: CampaignRepository) = CampaignUseCases(repository)
    @Provides @Singleton fun applicationUseCases(repository: ApplicationRepository) = ApplicationUseCases(repository)
}
