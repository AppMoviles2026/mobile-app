package com.example.collabpro.campaign

import com.example.collabpro.auth.AuthFixtures
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.applications.*
import com.example.collabpro.features.campaign.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class ApplicationRulesTest {
    @Test fun `blank and overlong proposals are rejected and the limit is four thousand`() {
        assertNotNull(ApplicationProposal(" ").validation()); assertNotNull(ApplicationProposal("x".repeat(4001)).validation())
        assertNull(ApplicationProposal("x".repeat(4000)).validation())
    }
    @Test fun `only mandatory manual requirements require confirmations by UUID`() {
        val campaign = discoveryCampaign()
        assertTrue(ApplicationProposal("Propuesta").validation(campaign)!!.fieldErrors.containsKey("requirements.${campaign.requirements.single().id}"))
        assertNull(ApplicationProposal("Propuesta", campaign.requirements.map { it.id }.toSet()).validation(campaign))
        val automatic = campaign.copy(requirements = listOf(campaign.requirements.single().copy(ruleType = RequirementRule.NICHE_EQUALS, expectedValue = "Moda")))
        assertNull(ApplicationProposal("Propuesta").validation(automatic))
        assertNotNull(ApplicationProposal("Propuesta", setOf(automatic.requirements.single().id)).validation(automatic))
    }
    @Test fun `optional manual requirements may be confirmed but unknown IDs cannot`() {
        val campaign = discoveryCampaign().let { it.copy(requirements = it.requirements.map { row -> row.copy(mandatory = false) }) }
        assertNull(ApplicationProposal("Propuesta").validation(campaign)); assertNull(ApplicationProposal("Propuesta", campaign.requirements.map { it.id }.toSet()).validation(campaign))
        assertNotNull(ApplicationProposal("Propuesta", setOf(UUID.randomUUID())).validation(campaign))
    }
    @Test fun `write confirmation requires correct immutable identity confirmations status and version`() {
        val original = ownApplication().copy(confirmedRequirementIds = setOf(UUID.randomUUID()))
        val intent = ApplicationWrite(ApplicationWriteKind.UPDATE, original, "Nuevo mensaje")
        val updated = original.copy(message = intent.message, version = 1)
        assertTrue(intent.isConfirmedBy(updated)); assertFalse(intent.isConfirmedBy(updated.copy(version = 0)))
        assertFalse(intent.isConfirmedBy(updated.copy(confirmedRequirementIds = emptySet())))
        assertFalse(intent.isConfirmedBy(updated.copy(id = UUID.randomUUID())))
        assertFalse(intent.isConfirmedBy(updated.copy(status = ApplicationStatus.CANCELLED)))
    }
    @Test fun `cancel confirmation requires cancelled and advanced version without altering proposal`() {
        val original = ownApplication(); val intent = ApplicationWrite(ApplicationWriteKind.CANCEL, original)
        assertTrue(intent.isConfirmedBy(original.copy(status = ApplicationStatus.CANCELLED, version = 1)))
        assertFalse(intent.isConfirmedBy(original.copy(status = ApplicationStatus.CANCELLED)))
        assertFalse(intent.isConfirmedBy(original.copy(status = ApplicationStatus.CANCELLED, version = 1, message = "Otro mensaje")))
    }
    @Test fun `lookup scans later own pages before declaring no existing application`() = runTest {
        val repository = FakeApplications(); repeat(101) { ownApplication().let { repository.records[it.id] = it } }
        val target = repository.records.values.last()
        assertEquals(target, (FindOwnApplication(repository)(AuthFixtures.account.profileId, target.campaignId) as ApiResult.Success).value)
        assertEquals(listOf(0, 1), repository.pages.map { it.page })
    }
    @Test fun `lookup absence requires every page and returns null only on success`() = runTest {
        val repository = FakeApplications(); repeat(101) { ownApplication().let { repository.records[it.id] = it } }
        assertNull((FindOwnApplication(repository)(AuthFixtures.account.profileId, UUID.randomUUID()) as ApiResult.Success).value)
        assertEquals(2, repository.pages.size)
    }
    @Test fun `a failed later page is not interpreted as absence`() = runTest {
        val repository = FakeApplications()
        repository.mineCall = { page -> if (page.page == 0) ApiResult.Success(Page(List(100) { ownApplication() }, 101, 0, 100)) else CampaignFixtures.failure() }
        assertTrue(FindOwnApplication(repository)(AuthFixtures.account.profileId, UUID.randomUUID()) is ApiResult.Failure)
    }
    @Test fun `a short intermediate page cannot falsely confirm absence`() = runTest {
        val repository = FakeApplications()
        repository.mineCall = { page -> ApiResult.Success(Page(listOf(ownApplication()), 101, page.page, 100)) }
        assertTrue(FindOwnApplication(repository)(AuthFixtures.account.profileId, UUID.randomUUID()) is ApiResult.Failure)
        assertEquals(1, repository.pages.size)
    }
    @Test fun `lookup rejects wrong owner wrong pagination or changing totals`() = runTest {
        val repository = FakeApplications(); val lookup = FindOwnApplication(repository)
        repository.mineCall = { ApiResult.Success(Page(listOf(ownApplication().copy(creatorId = UUID.randomUUID())), 1, 0, 100)) }
        assertTrue(lookup(AuthFixtures.account.profileId, UUID.randomUUID()) is ApiResult.Failure)
        repository.mineCall = { ApiResult.Success(Page(emptyList(), 0, 1, 100)) }
        assertTrue(lookup(AuthFixtures.account.profileId, UUID.randomUUID()) is ApiResult.Failure)
        repository.mineCall = { page -> ApiResult.Success(Page(if (page.page == 0) List(100) { ownApplication() } else listOf(ownApplication()), if (page.page == 0) 101 else 102, page.page, 100)) }
        assertTrue(lookup(AuthFixtures.account.profileId, UUID.randomUUID()) is ApiResult.Failure)
    }
}
