package com.example.collabpro.features.campaign.application.usecases

import com.example.collabpro.core.domain.PageRequest
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.domain.repositories.*
import java.util.UUID

class CreateCampaign(private val repository: CampaignRepository) {
    suspend operator fun invoke(campaign: NewCampaign, key: IdempotencyKey) = repository.create(campaign, key)
}
class SaveCampaignConditions(private val repository: CampaignRepository) {
    suspend operator fun invoke(id: UUID, conditions: CampaignConditions) = repository.saveConditions(id, conditions)
}
class PublishCampaign(private val repository: CampaignRepository) {
    suspend operator fun invoke(id: UUID) = repository.publish(id)
}
class GetOwnCampaigns(private val repository: CampaignRepository) {
    suspend operator fun invoke(page: PageRequest = PageRequest()) = repository.mine(page)
}
class GetPublishedCampaigns(private val repository: CampaignRepository) {
    suspend operator fun invoke(page: PageRequest = PageRequest()) = repository.published(page)
}
class SearchCampaigns(private val repository: CampaignRepository) {
    suspend operator fun invoke(filters: CampaignSearch, page: PageRequest = PageRequest()) = repository.search(filters, page)
}
class GetCampaignDetails(private val repository: CampaignRepository) {
    suspend operator fun invoke(id: UUID) = repository.details(id)
}
class DiscardCampaignDraft(private val repository: CampaignRepository) {
    suspend operator fun invoke(id: UUID) = repository.discardDraft(id)
}
class CloseCampaign(private val repository: CampaignRepository) {
    suspend operator fun invoke(id: UUID) = repository.close(id)
}
class SubmitApplication(private val repository: ApplicationRepository) {
    suspend operator fun invoke(campaignId: UUID, message: String, confirmedRequirementIds: Set<UUID>, key: IdempotencyKey) =
        repository.submit(campaignId, message, confirmedRequirementIds, key)
}
class GetOwnApplications(private val repository: ApplicationRepository) {
    suspend operator fun invoke(page: PageRequest = PageRequest()) = repository.mine(page)
}
class GetApplicationDetails(private val repository: ApplicationRepository) {
    suspend operator fun invoke(id: UUID) = repository.details(id)
}
class UpdateApplicationMessage(private val repository: ApplicationRepository) {
    suspend operator fun invoke(id: UUID, message: String, expectedVersion: Long) = repository.updateMessage(id, message, expectedVersion)
}
class CancelApplication(private val repository: ApplicationRepository) {
    suspend operator fun invoke(id: UUID, expectedVersion: Long) = repository.cancel(id, expectedVersion)
}

class CampaignUseCases(repository: CampaignRepository) {
    val create = CreateCampaign(repository)
    val saveConditions = SaveCampaignConditions(repository)
    val publish = PublishCampaign(repository)
    val getOwn = GetOwnCampaigns(repository)
    val getPublished = GetPublishedCampaigns(repository)
    val search = SearchCampaigns(repository)
    val getDetails = GetCampaignDetails(repository)
    val discardDraft = DiscardCampaignDraft(repository)
    val close = CloseCampaign(repository)
}
class ApplicationUseCases(repository: ApplicationRepository) {
    val submit = SubmitApplication(repository)
    val getOwn = GetOwnApplications(repository)
    val getDetails = GetApplicationDetails(repository)
    val updateMessage = UpdateApplicationMessage(repository)
    val cancel = CancelApplication(repository)
}
