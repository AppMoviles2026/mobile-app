package com.example.collabpro.campaign

import androidx.lifecycle.ViewModelStore
import com.example.collabpro.auth.*
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.applications.*
import com.example.collabpro.features.campaign.application.usecases.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.presentation.applications.*
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class OwnApplicationsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeApplications()
    private val campaignRepository = FakeDiscovery()
    private val identity = FakeIdentity()
    private val clock = MutableClock()
    private val authentication = AuthenticationSession(identity, MemorySessions(), clock)
    private val store = ViewModelStore()
    private lateinit var vm: OwnApplicationsViewModel
    private val campaignId get() = campaignRepository.record.summary.id
    @Before fun setup() {
        vm = OwnApplicationsViewModel(ApplicationUseCases(repository), CampaignUseCases(campaignRepository), FindOwnApplication(repository), authentication, clock)
        store.put("applications", vm)
    }
    @After fun cleanup() { store.clear() }
    private suspend fun login() { authentication.signIn("creator@example.test", "Password123!") }
    private fun begin() { vm.beginProposal(campaignId) }
    private fun fill() { vm.editMessage("Mi propuesta local"); vm.ui.value.form.campaign!!.requirements.filter { it.ruleType == RequirementRule.MANUAL_CONFIRMATION }.forEach { vm.confirmRequirement(it.id, true) } }
    private fun seed(status: ApplicationStatus = ApplicationStatus.PENDING): Application = ownApplication(campaignId).copy(status = status,
        confirmedRequirementIds = campaignRepository.record.requirements.map { it.id }.toSet()).also { repository.records[it.id] = it }
    private fun edit(application: Application) { vm.openDetails(application.id) }

    @Test fun `unverified sessions and brands cannot access own applications`() = runTest(main.dispatcher) {
        runCurrent(); begin(); vm.loadList(); vm.openDetails(UUID.randomUUID()); vm.submit(); runCurrent()
        identity.loginResult = ApiResult.Success(AuthFixtures.session.copy(account = AuthFixtures.account.copy(accountType = AccountType.BRAND)))
        login(); runCurrent(); begin(); vm.loadList(); runCurrent()
        assertTrue(repository.pages.isEmpty()); assertTrue(repository.submitted.isEmpty()); assertTrue(repository.detailIds.isEmpty())
    }
    @Test fun `new proposal verifies campaign and absence across real own pages first`() = runTest(main.dispatcher) {
        login(); runCurrent(); begin(); runCurrent()
        assertEquals(campaignId, vm.ui.value.form.campaign!!.summary.id); assertTrue(vm.ui.value.form.checkedAbsence)
        assertEquals(PageRequest(0, 100), repository.pages.single()); assertEquals(OwnApplicationView.FORM, vm.ui.value.navigate)
    }
    @Test fun `existing proposal is preloaded with server message confirmations and version`() = runTest(main.dispatcher) {
        login(); runCurrent(); val existing = seed().copy(version = 4); repository.records[existing.id] = existing
        begin(); runCurrent()
        assertEquals(existing, vm.ui.value.form.application); assertEquals(existing.message, vm.ui.value.form.proposal.message)
        assertEquals(existing.confirmedRequirementIds, vm.ui.value.form.proposal.confirmations); assertFalse(vm.ui.value.form.checkedAbsence)
        vm.confirmRequirement(existing.confirmedRequirementIds.single(), false); vm.submit(); runCurrent()
        assertTrue(repository.submitted.isEmpty()); assertEquals(existing.confirmedRequirementIds, vm.ui.value.form.proposal.confirmations)
    }
    @Test fun `lookup error is not absence and message survives retry`() = runTest(main.dispatcher) {
        login(); runCurrent(); begin(); runCurrent(); vm.editMessage("Texto que no se pierde")
        repository.mineCall = { CampaignFixtures.failure() }; vm.verifyForm(); runCurrent()
        vm.submit(); runCurrent(); assertTrue(repository.submitted.isEmpty()); assertEquals("Texto que no se pierde", vm.ui.value.form.proposal.message)
        // A previously verified absence must be revoked on every failed refresh.
        assertFalse(vm.ui.value.form.checkedAbsence)
    }
    @Test fun `required manual confirmations and message validation prevent invalid submission`() = runTest(main.dispatcher) {
        login(); runCurrent(); begin(); runCurrent(); vm.submit(); assertNotNull(vm.ui.value.form.failure)
        vm.editMessage("Mi propuesta"); vm.submit()
        assertTrue(vm.ui.value.form.failure!!.fieldErrors.keys.any { it.startsWith("requirements.") }); assertTrue(repository.submitted.isEmpty())
        fill(); vm.editMessage("x".repeat(4001)); vm.submit(); assertTrue(repository.submitted.isEmpty())
    }
    @Test fun `valid send posts only manual UUID confirmations and verified server role`() = runTest(main.dispatcher) {
        login(); runCurrent(); begin(); runCurrent(); fill(); vm.confirmRequirement(UUID.randomUUID(), true); vm.submit(); vm.submit(); runCurrent()
        assertEquals(1, repository.submitted.size); assertEquals(campaignRepository.record.requirements.map { it.id }.toSet(), repository.submitted.single().second)
        assertEquals(ApplicationStatus.PENDING, vm.ui.value.detail.application!!.status); assertEquals(OwnApplicationView.DETAIL, vm.ui.value.navigate)
        assertEquals(AuthFixtures.account.accountId, repository.actors.last()!!.accountId); assertEquals(AuthFixtures.session.expiresAt, repository.actors.last()!!.expiresAt)
    }
    @Test fun `unmet automatic requirement error is retained by UUID without simulating confirmation`() = runTest(main.dispatcher) {
        login(); runCurrent(); val automatic = Requirement(UUID.randomUUID(), "Debes tener nicho Moda", true, RequirementRule.NICHE_EQUALS, "Moda")
        campaignRepository.details = { ApiResult.Success(campaignRepository.record.copy(requirements = campaignRepository.record.requirements + automatic)) }
        begin(); runCurrent(); fill(); vm.confirmRequirement(automatic.id, true)
        repository.submitCall = { _, _, _, _ -> ApiResult.Failure(ApiFailure(FailureKind.REQUIREMENTS_NOT_MET, "REQUIREMENTS_NOT_MET", "No cumples requisitos", mapOf("requirements.${automatic.id}" to automatic.description))) }
        vm.submit(); runCurrent()
        assertFalse(automatic.id in repository.submitted.single().second); assertEquals(automatic.description, vm.ui.value.form.failure!!.fieldErrors["requirements.${automatic.id}"])
        assertEquals("Mi propuesta local", vm.ui.value.form.proposal.message); assertNull(vm.ui.value.form.submission)
    }
    @Test fun `closed or locally expired campaign cannot start a new submit`() = runTest(main.dispatcher) {
        login(); runCurrent(); begin(); runCurrent(); fill(); clock.time = campaignRepository.record.summary.applicationDeadline!!
        vm.submit(); runCurrent(); assertTrue(repository.submitted.isEmpty()); assertEquals("CAMPAIGN_NOT_ACCEPTING_APPLICATIONS", vm.ui.value.form.failure!!.code)
    }
    @Test fun `pending application on a closed campaign can still edit only its message`() = runTest(main.dispatcher) {
        login(); runCurrent(); val existing = seed()
        campaignRepository.details = { ApiResult.Success(campaignRepository.record.copy(summary = campaignRepository.record.summary.copy(status = CampaignStatus.CLOSED, acceptsApplications = false))) }
        begin(); runCurrent(); vm.editMessage("Editada tras cierre"); vm.saveMessage(); runCurrent()
        assertEquals("Editada tras cierre", repository.records[existing.id]!!.message); assertEquals(existing.confirmedRequirementIds, repository.records[existing.id]!!.confirmedRequirementIds)
    }
    @Test fun `lost submit response keeps frozen body key and prevents duplicate insertion on retry`() = runTest(main.dispatcher) {
        login(); runCurrent(); begin(); runCurrent(); fill(); repository.loseSubmit = true; vm.submit(); runCurrent()
        assertNotNull(vm.ui.value.form.submission); vm.editMessage("No debe reemplazar el intento")
        vm.submit(); runCurrent()
        assertEquals(1, repository.records.size); assertEquals(2, repository.submitted.size); assertEquals(repository.submitted[0], repository.submitted[1])
        assertNull(vm.ui.value.form.submission); assertEquals("Mi propuesta local", vm.ui.value.detail.application!!.message)
    }
    @Test fun `idempotent replay is followed by GET so an old pending snapshot cannot hide cancellation`() = runTest(main.dispatcher) {
        login(); runCurrent(); begin(); runCurrent(); fill(); repository.loseSubmit = true; vm.submit(); runCurrent()
        val remote = repository.records.values.single(); repository.records[remote.id] = remote.copy(status = ApplicationStatus.CANCELLED, version = 1)
        vm.submit(); runCurrent(); assertEquals(ApplicationStatus.CANCELLED, vm.ui.value.detail.application!!.status)
        assertEquals(1, repository.records.size)
    }
    @Test fun `duplicate discovered after preload opens existing actual detail while preserving local proposal`() = runTest(main.dispatcher) {
        login(); runCurrent(); begin(); runCurrent(); fill(); val existing = seed(); vm.submit(); runCurrent()
        assertEquals(existing.id, vm.ui.value.detail.id); assertEquals(existing.message, vm.ui.value.detail.application!!.message)
        assertEquals("Mi propuesta local", vm.ui.value.form.proposal.message); assertEquals(1, repository.records.size)
    }
    @Test fun `known duplicate that cannot be located never enables another new submission`() = runTest(main.dispatcher) {
        login(); runCurrent(); begin(); runCurrent(); fill()
        repository.submitCall = { _, _, _, _ -> repository.failure("APPLICATION_ALREADY_EXISTS") }; repository.mineCall = { CampaignFixtures.failure() }
        vm.submit(); runCurrent(); vm.submit(); runCurrent()
        assertEquals(1, repository.submitted.size); assertFalse(vm.ui.value.form.checkedAbsence)
        repository.mineCall = null; vm.verifyForm(); runCurrent(); assertFalse(vm.ui.value.form.checkedAbsence); assertTrue(vm.ui.value.form.duplicateKnown)
    }
    @Test fun `list uses server pagination and errors are not empty success`() = runTest(main.dispatcher) {
        login(); runCurrent(); repeat(21) { ownApplication().let { repository.records[it.id] = it } }
        vm.loadList(); runCurrent(); assertTrue(vm.ui.value.list.hasNext); vm.nextPage(); runCurrent()
        assertEquals(1, vm.ui.value.list.page!!.page); assertEquals(1, vm.ui.value.list.page!!.items.size); assertFalse(vm.ui.value.list.hasNext)
        repository.mineCall = { CampaignFixtures.failure() }; vm.retryList(); runCurrent(); assertNull(vm.ui.value.list.page); assertNotNull(vm.ui.value.list.failure)
    }
    @Test fun `list rejects another creator even if account UUID happens to match`() = runTest(main.dispatcher) {
        login(); runCurrent(); val wrong = ownApplication().copy(creatorId = AuthFixtures.account.accountId)
        repository.mineCall = { ApiResult.Success(Page(listOf(wrong), 1, 0, 20)) }; vm.loadList(); runCurrent()
        assertEquals(FailureKind.MALFORMED_RESPONSE, vm.ui.value.list.failure!!.kind)
    }
    @Test fun `missing or wrong owner detail never uses another application as fallback`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.openDetails(UUID.randomUUID()); runCurrent(); assertEquals(FailureKind.NOT_FOUND, vm.ui.value.detail.failure!!.kind)
        repository.detailCall = { ApiResult.Success(ownApplication(id = it).copy(creatorId = UUID.randomUUID())) }; vm.openDetails(UUID.randomUUID()); runCurrent()
        assertNull(vm.ui.value.detail.application); assertEquals(FailureKind.MALFORMED_RESPONSE, vm.ui.value.detail.failure!!.kind)
    }
    @Test fun `edit sends the originally consulted expectedVersion and preserves confirmations`() = runTest(main.dispatcher) {
        login(); runCurrent(); val existing = seed().copy(version = 5); repository.records[existing.id] = existing
        edit(existing); runCurrent(); vm.editSelected(); runCurrent(); vm.editMessage("Editada"); vm.saveMessage(); runCurrent()
        assertEquals(listOf("update" to 5L), repository.versions); assertEquals(6L, vm.ui.value.detail.application!!.version)
        assertEquals(existing.confirmedRequirementIds, vm.ui.value.detail.application!!.confirmedRequirementIds)
    }
    @Test fun `concurrent edit retains local text and cannot auto retry with a new version`() = runTest(main.dispatcher) {
        login(); runCurrent(); val existing = seed(); begin(); runCurrent(); vm.editMessage("Mi edición local")
        repository.records[existing.id] = existing.copy(message = "Edición desde otro dispositivo", version = 1)
        vm.saveMessage(); runCurrent(); assertEquals(0L, vm.ui.value.form.application!!.version); assertEquals(1L, vm.ui.value.form.latest!!.version)
        assertEquals("Mi edición local", vm.ui.value.form.proposal.message); vm.saveMessage(); runCurrent(); assertEquals(1, repository.versions.size)
        vm.useLatest(true); assertEquals("Mi edición local", vm.ui.value.form.proposal.message); vm.saveMessage(); runCurrent()
        assertEquals(listOf("update" to 0L, "update" to 1L), repository.versions); assertEquals(2L, repository.records[existing.id]!!.version)
    }
    @Test fun `explicit reload discards local changes only after user choice`() = runTest(main.dispatcher) {
        login(); runCurrent(); val existing = seed(); begin(); runCurrent(); vm.editMessage("Local")
        repository.records[existing.id] = existing.copy(message = "Servidor", version = 2); vm.verifyForm(); runCurrent()
        assertEquals("Local", vm.ui.value.form.proposal.message); assertEquals(0L, vm.ui.value.form.application!!.version)
        vm.useLatest(false); assertEquals("Servidor", vm.ui.value.form.proposal.message); assertEquals(2L, vm.ui.value.form.application!!.version)
    }
    @Test fun `terminal status discovered on conflict prevents further edit and cancellation`() = runTest(main.dispatcher) {
        login(); runCurrent(); val existing = seed(); begin(); runCurrent(); vm.editMessage("Local")
        repository.records[existing.id] = existing.copy(status = ApplicationStatus.SELECTED, version = 1); vm.saveMessage(); runCurrent()
        vm.useLatest(true); assertNotNull(vm.ui.value.form.latest); vm.useLatest(false); assertTrue(vm.ui.value.form.readOnly)
        vm.saveMessage(); vm.cancelSelected(); runCurrent(); assertEquals(1, repository.versions.size)
    }
    @Test fun `cancellation sends displayed version and retains cancelled record without resubmitting`() = runTest(main.dispatcher) {
        login(); runCurrent(); val existing = seed(); edit(existing); runCurrent(); vm.cancelSelected(); runCurrent()
        assertEquals(listOf("cancel" to 0L), repository.versions); assertEquals(ApplicationStatus.CANCELLED, vm.ui.value.detail.application!!.status)
        assertEquals(1, repository.records.size); vm.cancelSelected(); begin(); runCurrent(); vm.submit(); runCurrent()
        assertTrue(vm.ui.value.form.readOnly); assertTrue(repository.submitted.isEmpty())
    }
    @Test fun `cancellation conflict displays fresh state and does not cancel a newer unseen proposal`() = runTest(main.dispatcher) {
        login(); runCurrent(); val existing = seed(); edit(existing); runCurrent()
        repository.records[existing.id] = existing.copy(message = "Nuevo mensaje", version = 3); vm.cancelSelected(); runCurrent()
        assertEquals(ApplicationStatus.PENDING, repository.records[existing.id]!!.status); assertEquals(3L, vm.ui.value.detail.application!!.version)
        assertEquals(listOf("cancel" to 0L), repository.versions); assertNotNull(vm.ui.value.form.latest)
    }
    @Test fun `lost update or cancellation response is reconciled by GET without a second write`() = runTest(main.dispatcher) {
        login(); runCurrent(); val existing = seed(); begin(); runCurrent(); vm.editMessage("Guardada"); repository.loseUpdate = true
        vm.saveMessage(); runCurrent(); assertEquals("Guardada", vm.ui.value.detail.application!!.message); assertNull(vm.ui.value.form.pendingWrite)
        repository.loseCancel = true; vm.cancelSelected(); runCurrent()
        assertEquals(ApplicationStatus.CANCELLED, vm.ui.value.detail.application!!.status); assertEquals(2, repository.versions.size)
    }
    @Test fun `failed verification of uncertain write locks edits until explicit verification`() = runTest(main.dispatcher) {
        login(); runCurrent(); val existing = seed(); edit(existing); runCurrent(); vm.editSelected(); runCurrent(); vm.editMessage("Cambio incierto")
        repository.loseUpdate = true; repository.detailCall = { CampaignFixtures.failure() }; vm.saveMessage(); runCurrent()
        assertNotNull(vm.ui.value.form.pendingWrite); assertTrue(vm.ui.value.detail.pendingOperation)
        vm.editMessage("No reemplazar"); vm.saveMessage(); vm.cancelSelected(); runCurrent(); assertEquals(1, repository.versions.size)
        repository.detailCall = null; vm.verifySelected(); runCurrent()
        assertNull(vm.ui.value.form.pendingWrite); assertEquals("Cambio incierto", vm.ui.value.detail.application!!.message); assertEquals(1, repository.versions.size)
    }
    @Test fun `unknown failed write with unchanged server version can retry that same version`() = runTest(main.dispatcher) {
        login(); runCurrent(); seed(); begin(); runCurrent(); vm.editMessage("Cambio")
        repository.updateCall = { _, _, _ -> CampaignFixtures.failure() }; vm.saveMessage(); runCurrent()
        assertNull(vm.ui.value.form.pendingWrite); assertNull(vm.ui.value.form.latest)
        repository.updateCall = null; vm.saveMessage(); runCurrent(); assertEquals(listOf("update" to 0L, "update" to 0L), repository.versions)
    }
    @Test fun `malformed write response never becomes confirmed success`() = runTest(main.dispatcher) {
        login(); runCurrent(); val existing = seed(); begin(); runCurrent(); vm.editMessage("Cambio")
        repository.updateCall = { _, message, _ -> ApiResult.Success(existing.copy(message = message)) }; vm.saveMessage(); runCurrent()
        assertEquals(FailureKind.MALFORMED_RESPONSE, vm.ui.value.form.failure!!.kind); assertNotNull(vm.ui.value.form.pendingWrite)
    }
    @Test fun `navigation away and back retains unsent local proposal during current session`() = runTest(main.dispatcher) {
        login(); runCurrent(); begin(); runCurrent(); fill(); vm.onShown(OwnApplicationView.LIST); runCurrent(); begin(); runCurrent()
        assertEquals("Mi propuesta local", vm.ui.value.form.proposal.message); assertEquals(campaignRepository.record.requirements.map { it.id }.toSet(), vm.ui.value.form.proposal.confirmations)
    }
    @Test fun `logout and another account clear drafts navigation and pending requests`() = runTest(main.dispatcher) {
        login(); runCurrent(); begin(); runCurrent(); fill(); val reply = CompletableDeferred<ApiResult<Application>>()
        repository.submitCall = { _, _, _, _ -> withContext(NonCancellable) { reply.await() } }; vm.submit(); runCurrent()
        authentication.signOut(); runCurrent(); reply.complete(ApiResult.Success(ownApplication(campaignId))); runCurrent()
        assertNull(vm.ui.value.ownerId); assertNull(vm.ui.value.detail.application); assertEquals("", vm.ui.value.form.proposal.message)
        identity.loginResult = ApiResult.Success(AuthFixtures.session.copy(account = AuthFixtures.account.copy(accountId = UUID.randomUUID(), profileId = UUID.randomUUID())))
        login(); runCurrent(); assertEquals("", vm.ui.value.form.proposal.message); assertNull(vm.ui.value.navigate)
    }
    @Test fun `late former detail cannot replace newly selected application`() = runTest(main.dispatcher) {
        login(); runCurrent(); val first = seed(); val second = ownApplication(); repository.records[second.id] = second
        val reply = CompletableDeferred<ApiResult<Application>>()
        repository.detailCall = { if (it == first.id) withContext(NonCancellable) { reply.await() } else ApiResult.Success(second) }
        vm.openDetails(first.id); runCurrent(); vm.openDetails(second.id); runCurrent(); reply.complete(ApiResult.Success(first)); runCurrent()
        assertEquals(second.id, vm.ui.value.detail.application!!.id)
    }
    @Test fun `resume refreshes the visible list but does not send or cancel automatically`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.onShown(OwnApplicationView.LIST); runCurrent(); vm.onResume(); runCurrent()
        assertEquals(2, repository.pages.size); vm.onShown(null); vm.onResume(); runCurrent(); assertEquals(2, repository.pages.size)
        assertTrue(repository.submitted.isEmpty()); assertTrue(repository.versions.isEmpty())
    }
}
