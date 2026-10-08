package com.example.collabpro.campaign

import com.example.collabpro.auth.*
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.drafts.*
import com.example.collabpro.features.campaign.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CampaignPreparationTest {
    private val repository = FakeCampaigns()
    private val clock = MutableClock()
    private val workflow = CampaignPreparation(repository, clock)
    private var saved = CampaignFixtures.draft
    private val snapshots = mutableListOf<CampaignDraft>()
    private suspend fun execute(goal: PreparationGoal = PreparationGoal.PUBLICATION): ApiResult<CampaignDetails> =
        workflow.execute(CampaignFixtures.brandId, saved, goal) { draft, _ -> saved = draft; snapshots.add(draft); ApiResult.Success(Unit) }

    @Test fun `full preparation checkpoints creation conditions and publication in order`() = runTest {
        assertTrue(execute() is ApiResult.Success)
        assertEquals(listOf("create", "details", "conditions", "publish"), repository.events)
        assertTrue(snapshots.first().creation != null); assertEquals(DraftOperation.CREATE, snapshots.first().pending)
        assertTrue(saved.conditionsSaved); assertNotNull(saved.serverId); assertNull(saved.pending)
        assertEquals(CampaignStatus.OPEN, repository.records.values.single().summary.status)
    }
    @Test fun `basic draft can be saved without complete conditions and is not published`() = runTest {
        saved = CampaignDraft(basics = CampaignFixtures.basics)
        assertTrue(execute(PreparationGoal.BASIC_DRAFT) is ApiResult.Success)
        assertEquals(listOf("create", "details"), repository.events); assertEquals(CampaignStatus.DRAFT, repository.records.values.single().summary.status)
    }
    @Test fun `invalid metadata cannot create remote resources`() = runTest {
        saved = saved.copy(basics = saved.basics.copy(title = ""))
        assertTrue(execute() is ApiResult.Failure); assertTrue(repository.events.isEmpty())
    }
    @Test fun `invalid conditions block publication before first creation`() = runTest {
        saved = saved.copy(conditions = saved.conditions.copy(amount = "-1"))
        assertTrue(execute() is ApiResult.Failure); assertTrue(repository.events.isEmpty())
    }
    @Test fun `checkpoint failure prevents external writes`() = runTest {
        val result = workflow.execute(CampaignFixtures.brandId, saved, PreparationGoal.PUBLICATION) { _, _ -> CampaignFixtures.failure(FailureKind.STORAGE) }
        assertEquals(FailureKind.STORAGE, (result as ApiResult.Failure).error.kind); assertTrue(repository.events.isEmpty())
    }
    @Test fun `lost create response reuses same key body and single campaign`() = runTest {
        repository.createAfterCommitFailure = CampaignFixtures.failure(FailureKind.TIMEOUT)
        assertTrue(execute() is ApiResult.Failure); assertNull(saved.serverId); assertEquals(DraftOperation.CREATE, saved.pending)
        val intent = saved.creation
        assertTrue(execute() is ApiResult.Success); assertEquals(1, repository.records.size)
        assertEquals(listOf(intent!!.key, intent.key), repository.keys); assertEquals(repository.bodies[0], repository.bodies[1])
    }
    @Test fun `create validation failure releases metadata lock but never publishes`() = runTest {
        repository.createFailure = CampaignFixtures.failure(FailureKind.VALIDATION, 400)
        assertTrue(execute() is ApiResult.Failure); assertNull(saved.creation); assertNull(saved.pending); assertNull(saved.serverId)
    }
    @Test fun `failed conditions keep created UUID and retry never creates another`() = runTest {
        repository.conditionsFailure = CampaignFixtures.failure(FailureKind.REQUIREMENTS_NOT_MET, 422)
        assertTrue(execute() is ApiResult.Failure); val id = saved.serverId
        assertNotNull(id); assertFalse(saved.conditionsSaved); assertNull(saved.pending)
        repository.conditionsFailure = null; assertTrue(execute() is ApiResult.Success)
        assertEquals(id, saved.serverId); assertEquals(1, repository.events.count { it == "create" })
    }
    @Test fun `lost conditions response reconciles GET and does not repeat already matching PUT`() = runTest {
        repository.conditionsAfterCommitFailure = CampaignFixtures.failure(FailureKind.NETWORK)
        assertTrue(execute() is ApiResult.Failure); assertEquals(DraftOperation.CONDITIONS, saved.pending)
        assertTrue(execute() is ApiResult.Success); assertEquals(1, repository.events.count { it == "conditions" })
    }
    @Test fun `lost publication response checks OPEN and does not publish again`() = runTest {
        repository.publishAfterCommitFailure = CampaignFixtures.failure(FailureKind.TIMEOUT)
        assertTrue(execute() is ApiResult.Failure); assertTrue(saved.conditionsSaved); assertEquals(DraftOperation.PUBLISH, saved.pending)
        assertTrue(execute() is ApiResult.Success); assertEquals(1, repository.events.count { it == "publish" }); assertNull(saved.pending)
    }
    @Test fun `save conditions preserves unpublished draft`() = runTest {
        assertTrue(execute(PreparationGoal.CONDITIONS) is ApiResult.Success)
        assertEquals(CampaignStatus.DRAFT, repository.records.values.single().summary.status); assertFalse(repository.events.contains("publish"))
    }
    @Test fun `expired idempotency protection refuses replay rather than risking duplicate`() = runTest {
        saved = saved.copy(creation = CreationIntent(saved.basics.request(), IdempotencyKey(), clock.instant().minusSeconds(86400)), pending = DraftOperation.CREATE)
        assertTrue(execute() is ApiResult.Failure); assertTrue(repository.events.isEmpty())
    }
    @Test fun `pending creation cannot change its original request`() = runTest {
        saved = saved.copy(creation = CreationIntent(saved.basics.copy(title = "Otra").request(), IdempotencyKey(), clock.instant()))
        assertTrue(execute() is ApiResult.Failure); assertTrue(repository.events.isEmpty())
    }
    @Test fun `cached initial create snapshot is checked before writes to published resource`() = runTest {
        assertTrue(execute() is ApiResult.Success)
        val id = saved.serverId!!; val initial = repository.records.getValue(id).copy(summary = repository.records.getValue(id).summary.copy(status = CampaignStatus.DRAFT))
        val intent = saved.creation!!; repository.createCall = { _, _ -> ApiResult.Success(initial) }
        saved = saved.copy(serverId = null, pending = DraftOperation.CREATE, creation = intent)
        assertTrue(execute() is ApiResult.Success); assertEquals(1, repository.events.count { it == "publish" })
    }
    @Test fun `wrong brand create or detail response cannot advance workflow`() = runTest {
        assertTrue(execute(PreparationGoal.BASIC_DRAFT) is ApiResult.Success)
        val id = saved.serverId!!; repository.records[id] = repository.records.getValue(id).let { it.copy(summary = it.summary.copy(brandId = UUID.randomUUID())) }
        assertEquals(FailureKind.MALFORMED_RESPONSE, (execute() as ApiResult.Failure).error.kind)
        assertFalse(repository.events.contains("conditions"))
    }
    @Test fun `confirmed publication can be recovered even after application deadline expired`() = runTest {
        assertTrue(execute() is ApiResult.Success)
        clock.time = java.time.Instant.parse("2030-04-01T00:00:00Z")
        assertTrue(execute() is ApiResult.Success)
        assertEquals(1, repository.events.count { it == "publish" })
    }
    @Test fun `server ordering by generated child ids does not cause repeated conditions writes`() = runTest {
        saved = saved.copy(conditions = saved.conditions.copy(requirements = saved.conditions.requirements + RequirementDraft(description = "Otra condición")))
        assertTrue(execute(PreparationGoal.CONDITIONS) is ApiResult.Success)
        val id = saved.serverId!!
        repository.records[id] = repository.records.getValue(id).let { it.copy(requirements = it.requirements.reversed()) }
        assertTrue(execute() is ApiResult.Success)
        assertEquals(1, repository.events.count { it == "conditions" })
    }
}
