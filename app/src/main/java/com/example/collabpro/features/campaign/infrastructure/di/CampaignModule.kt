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
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.gson.Gson
import java.time.Clock
import com.example.collabpro.features.campaign.application.drafts.*
import com.example.collabpro.features.campaign.infrastructure.drafts.*
import com.example.collabpro.features.campaign.application.applications.FindOwnApplication

@Module
@InstallIn(SingletonComponent::class)
internal object CampaignModule {
    @Provides @Singleton fun draftStore(@ApplicationContext context: Context, gson: Gson): CampaignDraftStore {
        val key = AndroidCampaignDraftKey()
        return EncryptedCampaignDraftStore(AndroidCampaignDraftStorage(context), key::get, gson)
    }
    @Provides fun preparation(repository: CampaignRepository, clock: Clock) = CampaignPreparation(repository, clock)
    @Provides @Singleton fun campaignsApi(retrofit: Retrofit): CampaignApi = retrofit.create(CampaignApi::class.java)
    @Provides @Singleton fun applicationsApi(retrofit: Retrofit): ApplicationApi = retrofit.create(ApplicationApi::class.java)
    @Provides @Singleton fun campaigns(api: CampaignApi, executor: ApiExecutor): CampaignRepository = RemoteCampaignRepository(api, executor)
    @Provides @Singleton fun applications(api: ApplicationApi, executor: ApiExecutor): ApplicationRepository = RemoteApplicationRepository(api, executor)
    @Provides @Singleton fun campaignUseCases(repository: CampaignRepository) = CampaignUseCases(repository)
    @Provides @Singleton fun applicationUseCases(repository: ApplicationRepository) = ApplicationUseCases(repository)
    @Provides fun findOwnApplication(repository: ApplicationRepository) = FindOwnApplication(repository)
}
