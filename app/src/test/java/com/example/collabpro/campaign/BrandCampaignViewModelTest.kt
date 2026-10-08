package com.example.collabpro.campaign

import androidx.lifecycle.ViewModelStore
import com.example.collabpro.auth.*
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.drafts.*
import com.example.collabpro.features.campaign.application.usecases.CampaignUseCases
import com.example.collabpro.features.campaign.presentation.manage.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.identity.application.auth.*
import com.example.collabpro.features.identity.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BrandCampaignViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeCampaigns()
    private val identity = FakeIdentity()
    private val sessions = MemorySessions()
    private val clock = MutableClock()
    private val authentication = AuthenticationSession(identity, sessions, clock)
    private val drafts = MemoryDrafts()
    private val owners = ViewModelStore()
    private lateinit var vm: BrandCampaignViewModel
    @Before fun setup() {
        identity.loginResult = ApiResult.Success(AuthFixtures.session.copy(account = AuthFixtures.account.copy(accountType = AccountType.BRAND)))
        vm = BrandCampaignViewModel(CampaignUseCases(repository), CampaignPreparation(repository, clock), drafts, authentication, clock)
        owners.put("campaign", vm)
    }
    @After fun cleanup() { owners.clear() }
    private suspend fun login() { authentication.signIn("brand@example.test", "password123") }
    private fun fill() { vm.editBasics { CampaignFixtures.basics }; vm.editConditions { CampaignFixtures.terms } }

    @Test fun `creator and unverified session cannot read or create owned campaigns`() = runTest(main.dispatcher) {
        runCurrent(); vm.loadCampaigns(); vm.prepare(PreparationGoal.BASIC_DRAFT); runCurrent(); assertTrue(repository.events.isEmpty())
        identity.loginResult = ApiResult.Success(AuthFixtures.session); login(); runCurrent(); vm.loadCampaigns(); runCurrent(); assertTrue(repository.events.isEmpty())
    }
    @Test fun `continue validates metadata but does not create campaign`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.continueToConditions(); assertNotNull(vm.ui.value.editor.failure)
        fill(); vm.continueToConditions(); runCurrent()
        assertEquals(CampaignView.TERMS, vm.ui.value.navigate); assertTrue(repository.events.isEmpty()); assertEquals(2, vm.ui.value.editor.draft?.step)
    }
    @Test fun `edits are automatically persisted under current owner`() = runTest(main.dispatcher) {
        login(); runCurrent(); fill(); assertTrue(vm.ui.value.editor.localSaving)
        advanceTimeBy(301); runCurrent()
        assertEquals(CampaignFixtures.basics, drafts.values[AuthFixtures.account.accountId]?.basics)
        assertEquals(CampaignFixtures.terms, drafts.values[AuthFixtures.account.accountId]?.conditions); assertFalse(vm.ui.value.editor.localSaving)
    }
    @Test fun `saved local draft restores after ViewModel recreation without duplicate create`() = runTest(main.dispatcher) {
        drafts.values[AuthFixtures.account.accountId] = CampaignFixtures.draft
        login(); runCurrent(); assertEquals(CampaignFixtures.draft, vm.ui.value.editor.draft)
        vm.openEditor(); assertEquals(CampaignView.TERMS, vm.ui.value.navigate); assertTrue(repository.events.isEmpty())
    }
    @Test fun `local persistence failure prevents network creation and preserves input`() = runTest(main.dispatcher) {
        login(); runCurrent(); fill(); drafts.failSave = true
        vm.prepare(PreparationGoal.PUBLICATION); runCurrent(); assertTrue(repository.events.isEmpty())
        assertNotNull(vm.ui.value.editor.storageFailure); assertEquals(CampaignFixtures.basics, vm.ui.value.editor.draft?.basics)
        drafts.failSave = false; vm.prepare(PreparationGoal.PUBLICATION); runCurrent(); assertEquals(1, repository.records.size)
    }
    @Test fun `double submit performs one workflow and locks saved metadata`() = runTest(main.dispatcher) {
        login(); runCurrent(); fill(); vm.prepare(PreparationGoal.BASIC_DRAFT); vm.prepare(PreparationGoal.BASIC_DRAFT); runCurrent()
        assertEquals(1, repository.events.count { it == "create" }); assertTrue(vm.ui.value.editor.metadataLocked)
        vm.editBasics { copy(title = "Otro título") }; assertEquals("Título", vm.ui.value.editor.draft?.basics?.title)
    }
    @Test fun `partial success retains UUID draft fields and retry finishes publication`() = runTest(main.dispatcher) {
        login(); runCurrent(); fill(); repository.conditionsFailure = CampaignFixtures.failure(FailureKind.REQUIREMENTS_NOT_MET, 422)
        vm.prepare(PreparationGoal.PUBLICATION); runCurrent(); val id = vm.ui.value.editor.draft?.serverId
        assertNotNull(id); assertEquals(CampaignFixtures.terms, vm.ui.value.editor.draft?.conditions)
        assertNotNull(vm.ui.value.editor.failure); assertTrue(vm.ui.value.editor.notice!!.contains("se conserva"))
        repository.conditionsFailure = null; vm.prepare(PreparationGoal.PUBLICATION); runCurrent()
        assertEquals(id, vm.ui.value.editor.draft?.serverId); assertEquals(CampaignStatus.OPEN, vm.ui.value.editor.details?.summary?.status)
        assertTrue(vm.ui.value.editor.readOnly); assertEquals(1, repository.events.count { it == "create" })
    }
    @Test fun `lost publication stays uncertain until authoritative server check`() = runTest(main.dispatcher) {
        login(); runCurrent(); fill(); repository.publishAfterCommitFailure = CampaignFixtures.failure(FailureKind.TIMEOUT)
        vm.prepare(PreparationGoal.PUBLICATION); runCurrent(); assertEquals(DraftOperation.PUBLISH, vm.ui.value.editor.draft?.pending)
        assertEquals(CampaignStatus.DRAFT, vm.ui.value.editor.details?.summary?.status)
        vm.checkServer(); runCurrent(); assertEquals(CampaignStatus.OPEN, vm.ui.value.editor.details?.summary?.status)
        assertNull(vm.ui.value.editor.draft?.pending); assertTrue(vm.ui.value.editor.readOnly)
    }
    @Test fun `unresolved creation cannot be reset or have its metadata edited`() = runTest(main.dispatcher) {
        login(); runCurrent(); fill(); repository.createAfterCommitFailure = CampaignFixtures.failure(FailureKind.TIMEOUT)
        vm.prepare(PreparationGoal.PUBLICATION); runCurrent(); val key = vm.ui.value.editor.draft?.creation?.key
        vm.newDraft(); runCurrent(); vm.editBasics { copy(title = "Otro") }
        assertEquals(key, vm.ui.value.editor.draft?.creation?.key); assertEquals("Título", vm.ui.value.editor.draft?.basics?.title)
        assertEquals(FailureKind.CONFLICT, vm.ui.value.editor.failure?.kind)
    }
    @Test fun `logout clears memory but keeps encrypted owner-scoped preparation for later verified login`() = runTest(main.dispatcher) {
        login(); runCurrent(); fill(); advanceTimeBy(301); runCurrent(); authentication.signOut(); runCurrent()
        assertNull(vm.ui.value.editor.draft); assertNotNull(drafts.values[AuthFixtures.account.accountId])
        val other = AuthFixtures.account.copy(accountId = UUID.randomUUID(), profileId = UUID.randomUUID(), accountType = AccountType.BRAND)
        identity.loginResult = ApiResult.Success(AuthFixtures.session.copy(account = other)); login(); runCurrent()
        assertEquals("", vm.ui.value.editor.draft?.basics?.title)
    }
    @Test fun `late campaign creation cannot run conditions after account changes`() = runTest(main.dispatcher) {
        login(); runCurrent(); fill()
        val response = CompletableDeferred<ApiResult<CampaignDetails>>()
        repository.createCall = { _, _ -> withContext(NonCancellable) { response.await() } }
        vm.prepare(PreparationGoal.PUBLICATION); runCurrent(); authentication.signOut(); runCurrent()
        val fake = FakeCampaigns(); val created = fake.create(CampaignFixtures.basics.request(), IdempotencyKey())
        response.complete(created); runCurrent(); assertNull(vm.ui.value.editor.draft)
        assertFalse(repository.events.contains("conditions")); assertFalse(repository.events.contains("publish"))
    }
    @Test fun `own list error does not become empty success and retry is paginated`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.mineFailure = CampaignFixtures.failure()
        vm.loadCampaigns(); runCurrent(); assertNull(vm.ui.value.campaigns.page); assertNotNull(vm.ui.value.campaigns.failure)
        repository.mineFailure = null; vm.loadCampaigns(1); runCurrent(); assertEquals(1, vm.ui.value.campaigns.page?.page)
    }
    @Test fun `detail not found does not select first available demo campaign`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.openDetails(UUID.randomUUID()); runCurrent()
        assertNull(vm.ui.value.detail.details); assertEquals(FailureKind.NOT_FOUND, vm.ui.value.detail.failure?.kind)
    }
    @Test fun `owned draft can be opened and resumed from server`() = runTest(main.dispatcher) {
        login(); runCurrent(); val created = repository.create(CampaignFixtures.basics.request(), IdempotencyKey()) as ApiResult.Success
        vm.openDetails(created.value.summary.id); runCurrent(); vm.editSelected(); runCurrent()
        assertEquals(created.value.summary.id, vm.ui.value.editor.draft?.serverId); assertEquals(CampaignView.TERMS, vm.ui.value.navigate)
        assertEquals(CampaignFixtures.basics, vm.ui.value.editor.draft?.basics)
    }
    @Test fun `confirmed deletion clears current draft and refreshes list`() = runTest(main.dispatcher) {
        login(); runCurrent(); fill(); vm.prepare(PreparationGoal.BASIC_DRAFT); runCurrent(); val id = vm.ui.value.editor.draft!!.serverId!!
        vm.openDetails(id); runCurrent(); vm.discardOrClose(true); runCurrent()
        assertTrue(repository.records.isEmpty()); assertNull(vm.ui.value.editor.draft?.serverId); assertEquals(CampaignView.LIST, vm.ui.value.navigate)
    }
    @Test fun `published resource cannot be discarded and closure preserves campaign`() = runTest(main.dispatcher) {
        login(); runCurrent(); fill(); vm.prepare(PreparationGoal.PUBLICATION); runCurrent(); val id = vm.ui.value.editor.draft!!.serverId!!
        vm.openDetails(id); runCurrent(); vm.discardOrClose(true); runCurrent(); assertFalse(repository.events.contains("discard"))
        vm.discardOrClose(false); runCurrent(); assertEquals(CampaignStatus.CLOSED, vm.ui.value.detail.details?.summary?.status)
        assertTrue(repository.records.containsKey(id)); assertTrue(vm.ui.value.editor.readOnly)
    }
    @Test fun `new preparation leaves previous remote campaign untouched`() = runTest(main.dispatcher) {
        login(); runCurrent(); fill(); vm.prepare(PreparationGoal.BASIC_DRAFT); runCurrent(); vm.newDraft(); runCurrent()
        assertNull(vm.ui.value.editor.draft?.serverId); assertEquals(1, repository.records.size)
    }
    @Test fun `storage restoration failure does not fabricate draft and can be explicitly reset`() = runTest(main.dispatcher) {
        drafts.failLoad = true; login(); runCurrent(); assertNull(vm.ui.value.editor.draft); assertNotNull(vm.ui.value.editor.storageFailure)
        vm.newDraft(); runCurrent(); assertNotNull(vm.ui.value.editor.draft); assertNull(vm.ui.value.editor.storageFailure)
    }
}
