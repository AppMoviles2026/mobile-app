package com.example.collabpro.campaign

import com.example.collabpro.auth.AuthFixtures
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.drafts.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.domain.repositories.CampaignRepository
import java.util.UUID

internal object CampaignFixtures {
    val brandId = AuthFixtures.account.profileId
    val basics = CampaignBasics("Título", "Objetivo", "Descripción", "Moda", "Jóvenes", "Lima")
    val terms = ConditionsDraft(requirements = listOf(RequirementDraft(description = "Confirmar condiciones")),
        deliverables = listOf(DeliverableDraft(contentType = "Video", description = "Mostrar producto", deadline = "2030-03-01 12:00")),
        applicationDeadline = "2030-02-01 12:00", amount = "100.00", currency = "PEN", compensationDescription = "Pago por contenido", zoneId = "UTC")
    val draft = CampaignDraft(basics, terms, step = 2)
    fun failure(kind: FailureKind = FailureKind.NETWORK, status: Int? = null, code: String? = null) =
        ApiResult.Failure(ApiFailure(kind, code, "Fallo de prueba", httpStatus = status))
}
internal class MemoryDrafts : CampaignDraftStore {
    val values = mutableMapOf<UUID, CampaignDraft>()
    var failSave = false; var failLoad = false; var failClear = false
    var saves = 0
    override suspend fun load(owner: UUID): ApiResult<CampaignDraft?> = if (failLoad) CampaignFixtures.failure(FailureKind.STORAGE) else ApiResult.Success(values[owner])
    override suspend fun save(owner: UUID, draft: CampaignDraft): ApiResult<Unit> {
        saves++; if (failSave) return CampaignFixtures.failure(FailureKind.STORAGE)
        values[owner] = draft; return ApiResult.Success(Unit)
    }
    override suspend fun clear(owner: UUID): ApiResult<Unit> {
        if (failClear) return CampaignFixtures.failure(FailureKind.STORAGE)
        values.remove(owner); return ApiResult.Success(Unit)
    }
}
internal class FakeCampaigns : CampaignRepository {
    val records = linkedMapOf<UUID, CampaignDetails>()
    val keys = mutableListOf<IdempotencyKey>(); val bodies = mutableListOf<NewCampaign>()
    val events = mutableListOf<String>()
    var createFailure: ApiResult.Failure? = null; var conditionsFailure: ApiResult.Failure? = null
    var publishFailure: ApiResult.Failure? = null; var detailsFailure: ApiResult.Failure? = null
    var mineFailure: ApiResult.Failure? = null
    var createAfterCommitFailure: ApiResult.Failure? = null; var conditionsAfterCommitFailure: ApiResult.Failure? = null
    var publishAfterCommitFailure: ApiResult.Failure? = null
    var createCall: (suspend (NewCampaign, IdempotencyKey) -> ApiResult<CampaignDetails>)? = null
    var detailsCall: (suspend (UUID) -> ApiResult<CampaignDetails>)? = null
    private val idempotency = mutableMapOf<IdempotencyKey, UUID>()
    override suspend fun create(campaign: NewCampaign, key: IdempotencyKey): ApiResult<CampaignDetails> {
        events.add("create"); keys.add(key); bodies.add(campaign)
        createCall?.let { return it(campaign, key) }; createFailure?.let { return it }
        val id = idempotency.getOrPut(key) { UUID.randomUUID() }
        val existing = records.getOrPut(id) { CampaignDetails(CampaignSummary(id, CampaignFixtures.brandId, "Marca real", campaign.title,
            campaign.category, campaign.location, null, null, CampaignStatus.DRAFT, false), campaign.objective, campaign.description,
            campaign.targetAudience, null, emptyList(), emptyList()) }
        createAfterCommitFailure?.let { createAfterCommitFailure = null; return it }
        return ApiResult.Success(existing)
    }
    override suspend fun saveConditions(id: UUID, conditions: CampaignConditions): ApiResult<CampaignDetails> {
        events.add("conditions"); conditionsFailure?.let { return it }
        val current = records.getValue(id)
        val value = current.copy(summary = current.summary.copy(compensation = conditions.compensation, applicationDeadline = conditions.applicationDeadline),
            requirements = conditions.requirements.map { Requirement(UUID.randomUUID(), it.description, it.mandatory, it.ruleType, it.expectedValue) },
            deliverables = conditions.deliverables.map { DeliverableSpec(UUID.randomUUID(), it.contentType, it.description, it.quantity, it.deadline) })
        records[id] = value
        conditionsAfterCommitFailure?.let { conditionsAfterCommitFailure = null; return it }
        return ApiResult.Success(value)
    }
    override suspend fun publish(id: UUID): ApiResult<CampaignDetails> {
        events.add("publish"); publishFailure?.let { return it }
        val value = records.getValue(id).let { it.copy(summary = it.summary.copy(status = CampaignStatus.OPEN, acceptsApplications = true), publicationDate = AuthFixtures.now) }
        records[id] = value
        publishAfterCommitFailure?.let { publishAfterCommitFailure = null; return it }
        return ApiResult.Success(value)
    }
    override suspend fun details(id: UUID): ApiResult<CampaignDetails> {
        events.add("details"); detailsCall?.let { return it(id) }; detailsFailure?.let { return it }
        return records[id]?.let { ApiResult.Success(it) } ?: CampaignFixtures.failure(FailureKind.NOT_FOUND, 404)
    }
    override suspend fun mine(page: PageRequest): ApiResult<Page<CampaignSummary>> {
        events.add("mine"); mineFailure?.let { return it }
        return ApiResult.Success(Page(records.values.drop(page.page * page.size).take(page.size).map { it.summary }, records.size.toLong(), page.page, page.size))
    }
    override suspend fun discardDraft(id: UUID): ApiResult<Unit> { events.add("discard"); records.remove(id); return ApiResult.Success(Unit) }
    override suspend fun close(id: UUID): ApiResult<CampaignDetails> {
        events.add("close"); val closed = records.getValue(id).let { it.copy(summary = it.summary.copy(status = CampaignStatus.CLOSED, acceptsApplications = false)) }
        records[id] = closed; return ApiResult.Success(closed)
    }
    override suspend fun published(page: PageRequest): ApiResult<Page<CampaignSummary>> = error("Unexpected discovery call")
    override suspend fun search(filters: CampaignSearch, page: PageRequest): ApiResult<Page<CampaignSummary>> = error("Unexpected discovery call")
}
