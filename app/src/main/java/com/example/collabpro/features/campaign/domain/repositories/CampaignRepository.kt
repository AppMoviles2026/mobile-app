package com.example.collabpro.features.campaign.domain.repositories

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.domain.model.*
import java.util.UUID

interface CampaignRepository {
    suspend fun create(campaign: NewCampaign, key: IdempotencyKey): ApiResult<CampaignDetails>
    suspend fun saveConditions(id: UUID, conditions: CampaignConditions): ApiResult<CampaignDetails>
    suspend fun publish(id: UUID): ApiResult<CampaignDetails>
    suspend fun mine(page: PageRequest = PageRequest()): ApiResult<Page<CampaignSummary>>
    suspend fun published(page: PageRequest = PageRequest()): ApiResult<Page<CampaignSummary>>
    suspend fun search(filters: CampaignSearch, page: PageRequest = PageRequest()): ApiResult<Page<CampaignSummary>>
    suspend fun details(id: UUID): ApiResult<CampaignDetails>
    suspend fun discardDraft(id: UUID): ApiResult<Unit>
    suspend fun close(id: UUID): ApiResult<CampaignDetails>
}

interface ApplicationRepository {
    suspend fun submit(campaignId: UUID, message: String, confirmedRequirementIds: Set<UUID>, key: IdempotencyKey): ApiResult<Application>
    suspend fun mine(page: PageRequest = PageRequest()): ApiResult<Page<Application>>
    suspend fun details(id: UUID): ApiResult<Application>
    suspend fun updateMessage(id: UUID, message: String, expectedVersion: Long): ApiResult<Application>
    suspend fun cancel(id: UUID, expectedVersion: Long): ApiResult<Application>
}
