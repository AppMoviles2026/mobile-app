package com.example.collabpro.features.campaign.infrastructure

import com.example.collabpro.core.domain.PageRequest
import com.example.collabpro.core.infrastructure.network.ApiExecutor
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.domain.repositories.*
import com.example.collabpro.features.campaign.infrastructure.remote.*
import java.util.UUID

internal class RemoteCampaignRepository(private val api: CampaignApi, private val executor: ApiExecutor) : CampaignRepository {
    override suspend fun create(campaign: NewCampaign, key: IdempotencyKey) =
        executor.protectedCall({ api.create(it, key.value.toString(), campaign.toDto()) }) { it.toDetails() }
    override suspend fun saveConditions(id: UUID, conditions: CampaignConditions) =
        executor.protectedCall({ api.conditions(it, id.toString(), conditions.toDto()) }) { it.toDetails() }
    override suspend fun publish(id: UUID) = executor.protectedCall({ api.publish(it, id.toString()) }) { it.toDetails() }
    override suspend fun mine(page: PageRequest) = executor.protectedCall({ api.mine(it, page.page, page.size) }) { it.toDomain(CampaignDto::toSummary) }
    override suspend fun published(page: PageRequest) = executor.protectedCall({ api.published(it, page.page, page.size) }) { it.toDomain(CampaignDto::toSummary) }
    override suspend fun search(filters: CampaignSearch, page: PageRequest) = executor.protectedCall({
        api.search(it, filters.query, filters.category, filters.location, filters.compensationType?.name, page.page, page.size)
    }) { it.toDomain(CampaignDto::toSummary) }
    override suspend fun details(id: UUID) = executor.protectedCall({ api.details(it, id.toString()) }) { it.toDetails() }
    override suspend fun discardDraft(id: UUID) = executor.protectedUnit { api.discard(it, id.toString()) }
    override suspend fun close(id: UUID) = executor.protectedCall({ api.close(it, id.toString()) }) { it.toDetails() }
}

internal class RemoteApplicationRepository(private val api: ApplicationApi, private val executor: ApiExecutor) : ApplicationRepository {
    override suspend fun submit(campaignId: UUID, message: String, confirmedRequirementIds: Set<UUID>, key: IdempotencyKey) = executor.protectedCall({
        api.submit(it, campaignId.toString(), key.value.toString(), SubmitApplicationDto(message, confirmedRequirementIds.map(UUID::toString).toSet()))
    }) { it.toDomain() }
    override suspend fun mine(page: PageRequest) = executor.protectedCall({ api.mine(it, page.page, page.size) }) { it.toDomain(ApplicationDto::toDomain) }
    override suspend fun details(id: UUID) = executor.protectedCall({ api.details(it, id.toString()) }) { it.toDomain() }
    override suspend fun updateMessage(id: UUID, message: String, expectedVersion: Long) =
        executor.protectedCall({ api.update(it, id.toString(), UpdateApplicationDto(message, expectedVersion)) }) { it.toDomain() }
    override suspend fun cancel(id: UUID, expectedVersion: Long) =
        executor.protectedCall({ api.cancel(it, id.toString(), CancelApplicationDto(expectedVersion)) }) { it.toDomain() }
}
