package com.example.collabpro.campaign

import com.example.collabpro.auth.AuthFixtures
import com.example.collabpro.core.application.security.ExpectedAccount
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.domain.repositories.ApplicationRepository
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal fun ownApplication(campaignId: UUID = UUID.randomUUID(), id: UUID = UUID.randomUUID()) = Application(id, campaignId,
    AuthFixtures.account.profileId, "Campaña real", "Marca real", "Propuesta registrada", ApplicationStatus.PENDING, AuthFixtures.now, emptySet(), 0)
internal class FakeApplications : ApplicationRepository {
    val records = linkedMapOf<UUID, Application>()
    val submitted = mutableListOf<Triple<String, Set<UUID>, IdempotencyKey>>()
    val versions = mutableListOf<Pair<String, Long>>()
    val pages = mutableListOf<PageRequest>()
    val detailIds = mutableListOf<UUID>()
    val actors = mutableListOf<ExpectedAccount?>()
    var mineCall: (suspend (PageRequest) -> ApiResult<Page<Application>>)? = null
    var detailCall: (suspend (UUID) -> ApiResult<Application>)? = null
    var submitCall: (suspend (UUID, String, Set<UUID>, IdempotencyKey) -> ApiResult<Application>)? = null
    var updateCall: (suspend (UUID, String, Long) -> ApiResult<Application>)? = null
    var cancelCall: (suspend (UUID, Long) -> ApiResult<Application>)? = null
    var loseSubmit = false; var loseUpdate = false; var loseCancel = false
    private val keys = mutableMapOf<IdempotencyKey, Application>()
    fun failure(code: String, kind: FailureKind = FailureKind.CONFLICT) = ApiResult.Failure(ApiFailure(kind, code, code))
    override suspend fun submit(campaignId: UUID, message: String, confirmedRequirementIds: Set<UUID>, key: IdempotencyKey): ApiResult<Application> {
        submitted.add(Triple(message, confirmedRequirementIds, key)); actors.add(coroutineContext[ExpectedAccount])
        submitCall?.let { return it(campaignId, message, confirmedRequirementIds, key) }
        keys[key]?.let { return ApiResult.Success(it) }
        if (records.values.any { it.campaignId == campaignId }) return failure("APPLICATION_ALREADY_EXISTS")
        val value = ownApplication(campaignId).copy(message = message, confirmedRequirementIds = confirmedRequirementIds)
        records[value.id] = value; keys[key] = value
        if (loseSubmit) { loseSubmit = false; return CampaignFixtures.failure(FailureKind.TIMEOUT) }
        return ApiResult.Success(value)
    }
    override suspend fun mine(page: PageRequest): ApiResult<Page<Application>> {
        pages.add(page); actors.add(coroutineContext[ExpectedAccount]); mineCall?.let { return it(page) }
        return ApiResult.Success(Page(records.values.drop(page.page * page.size).take(page.size), records.size.toLong(), page.page, page.size))
    }
    override suspend fun details(id: UUID): ApiResult<Application> {
        detailIds.add(id); actors.add(coroutineContext[ExpectedAccount]); detailCall?.let { return it(id) }
        return records[id]?.let { ApiResult.Success(it) } ?: CampaignFixtures.failure(FailureKind.NOT_FOUND)
    }
    override suspend fun updateMessage(id: UUID, message: String, expectedVersion: Long): ApiResult<Application> {
        versions.add("update" to expectedVersion); actors.add(coroutineContext[ExpectedAccount]); updateCall?.let { return it(id, message, expectedVersion) }
        val original = records.getValue(id)
        if (original.version != expectedVersion) return failure("CONCURRENT_UPDATE")
        if (original.status != ApplicationStatus.PENDING) return failure("APPLICATION_NOT_PENDING")
        val value = original.copy(message = message, version = original.version + if (message == original.message) 0 else 1)
        records[id] = value
        if (loseUpdate) { loseUpdate = false; return CampaignFixtures.failure(FailureKind.NETWORK) }
        return ApiResult.Success(value)
    }
    override suspend fun cancel(id: UUID, expectedVersion: Long): ApiResult<Application> {
        versions.add("cancel" to expectedVersion); actors.add(coroutineContext[ExpectedAccount]); cancelCall?.let { return it(id, expectedVersion) }
        val original = records.getValue(id)
        if (original.version != expectedVersion) return failure("CONCURRENT_UPDATE")
        if (original.status != ApplicationStatus.PENDING) return failure("APPLICATION_NOT_PENDING")
        val value = original.copy(status = ApplicationStatus.CANCELLED, version = original.version + 1); records[id] = value
        if (loseCancel) { loseCancel = false; return CampaignFixtures.failure(FailureKind.TIMEOUT) }
        return ApiResult.Success(value)
    }
}
