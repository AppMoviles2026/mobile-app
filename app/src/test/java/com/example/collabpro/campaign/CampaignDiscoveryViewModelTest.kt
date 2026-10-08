package com.example.collabpro.campaign

import androidx.lifecycle.ViewModelStore
import com.example.collabpro.auth.*
import com.example.collabpro.core.application.security.ExpectedAccount
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.usecases.CampaignUseCases
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.domain.repositories.CampaignRepository
import com.example.collabpro.features.campaign.presentation.discovery.*
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal fun discoveryCampaign(id: UUID = UUID.randomUUID()) = CampaignDetails(CampaignSummary(id, UUID.randomUUID(), "Marca real",
    "Campaña real", "Moda", "Lima", Compensation(CompensationType.PRODUCT, null, null, "Productos"),
    AuthFixtures.now.plusSeconds(1800), CampaignStatus.OPEN, true), "Objetivo real", "Descripción real", "Audiencia real",
    AuthFixtures.now.minusSeconds(60), listOf(Requirement(UUID.randomUUID(), "Confirmar", true, RequirementRule.MANUAL_CONFIRMATION, null)),
    listOf(DeliverableSpec(UUID.randomUUID(), "Video", "Contenido", 2, AuthFixtures.now.plusSeconds(7200))))

internal class FakeDiscovery : CampaignRepository by FakeCampaigns() {
    val publishedCalls = mutableListOf<PageRequest>()
    val searches = mutableListOf<Pair<CampaignSearch, PageRequest>>()
    val detailCalls = mutableListOf<UUID>()
    val actors = mutableListOf<ExpectedAccount?>()
    val record = discoveryCampaign()
    var published: suspend (PageRequest) -> ApiResult<Page<CampaignSummary>> = { ApiResult.Success(Page(listOf(record.summary), 1, it.page, it.size)) }
    var search: suspend (CampaignSearch, PageRequest) -> ApiResult<Page<CampaignSummary>> = { _, page -> ApiResult.Success(Page(listOf(record.summary), 1, page.page, page.size)) }
    var details: suspend (UUID) -> ApiResult<CampaignDetails> = { ApiResult.Success(record.copy(summary = record.summary.copy(id = it))) }
    override suspend fun published(page: PageRequest): ApiResult<Page<CampaignSummary>> {
        publishedCalls.add(page); actors.add(coroutineContext[ExpectedAccount]); return published.invoke(page)
    }
    override suspend fun search(filters: CampaignSearch, page: PageRequest): ApiResult<Page<CampaignSummary>> {
        searches.add(filters to page); actors.add(coroutineContext[ExpectedAccount]); return search.invoke(filters, page)
    }
    override suspend fun details(id: UUID): ApiResult<CampaignDetails> { detailCalls.add(id); actors.add(coroutineContext[ExpectedAccount]); return details.invoke(id) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CampaignDiscoveryViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeDiscovery()
    private val identity = FakeIdentity()
    private val clock = MutableClock()
    private val authentication = AuthenticationSession(identity, MemorySessions(), clock)
    private val store = ViewModelStore()
    private lateinit var vm: CampaignDiscoveryViewModel
    @Before fun setup() { vm = CampaignDiscoveryViewModel(CampaignUseCases(repository), authentication, clock); store.put("discovery", vm) }
    @After fun cleanup() { store.clear() }
    private suspend fun login() { authentication.signIn("creator@example.test", "Password123!") }
    private fun test(block: suspend TestScope.() -> Unit) = runTest(main.dispatcher) {
        try { block() } finally { vm.onBackground() }
    }

    @Test fun `unverified sessions and brands never call creator discovery`() = test {
        runCurrent(); vm.onShown(DiscoveryView.HOME); vm.applyFilters(); vm.openDetails(UUID.randomUUID()); runCurrent()
        identity.loginResult = ApiResult.Success(AuthFixtures.session.copy(account = AuthFixtures.account.copy(accountType = AccountType.BRAND)))
        login(); runCurrent(); vm.onShown(DiscoveryView.SEARCH); vm.loadOpportunities(); runCurrent()
        assertTrue(repository.publishedCalls.isEmpty()); assertTrue(repository.searches.isEmpty()); assertTrue(repository.detailCalls.isEmpty())
    }
    @Test fun `home fetches three real published opportunities bound to verified initiating account`() = test {
        login(); runCurrent(); vm.onShown(DiscoveryView.HOME); runCurrent()
        assertEquals(listOf(PageRequest(0, 3)), repository.publishedCalls); assertEquals(repository.record.summary, vm.ui.value.opportunities.page!!.items.single())
        assertEquals(AuthFixtures.account.accountId, repository.actors.single()!!.accountId); assertEquals(AuthFixtures.session.expiresAt, repository.actors.single()!!.expiresAt)
    }
    @Test fun `initial exploration uses published endpoint and combined filters use search with trimmed values`() = test {
        login(); runCurrent(); vm.onShown(DiscoveryView.SEARCH); runCurrent()
        assertEquals(PageRequest(0, 20), repository.publishedCalls.single())
        vm.editFilters { copy(query = " Marca ", category = " Moda ", location = " Lima ", compensationType = CompensationType.PRODUCT) }
        vm.applyFilters(); runCurrent()
        assertEquals(CampaignSearch("Marca", "Moda", "Lima", CompensationType.PRODUCT), repository.searches.single().first)
        assertFalse(vm.ui.value.pendingFilters)
    }
    @Test fun `changing filters resets page and does not filter just the local page`() = test {
        login(); runCurrent(); repository.published = { ApiResult.Success(Page(listOf(repository.record.summary), 45, it.page, it.size)) }
        vm.onShown(DiscoveryView.SEARCH); runCurrent(); vm.nextPage(); runCurrent(); assertEquals(1, vm.ui.value.results.page!!.page)
        vm.editFilters { copy(category = "Moda") }; assertTrue(vm.ui.value.pendingFilters)
        vm.nextPage(); runCurrent(); assertEquals(2, repository.publishedCalls.size)
        vm.applyFilters(); runCurrent(); assertEquals(0, repository.searches.single().second.page)
    }
    @Test fun `clear resets all four filters and returns to first published page`() = test {
        login(); runCurrent(); vm.editFilters { copy(query = "marca", category = "moda", location = "Lima", compensationType = CompensationType.CASH) }
        vm.applyFilters(); runCurrent(); vm.clearFilters(); runCurrent()
        assertTrue(vm.ui.value.filters.isEmpty); assertTrue(vm.ui.value.appliedFilters.isEmpty)
        assertEquals(PageRequest(), repository.publishedCalls.single())
    }
    @Test fun `invalid filter lengths are shown without sending a request`() = test {
        login(); runCurrent(); vm.editFilters { copy(query = "x".repeat(201), category = "x".repeat(101), location = "x".repeat(151)) }
        vm.applyFilters(); runCurrent()
        assertEquals(setOf("q", "category", "location"), vm.ui.value.filterFailure!!.fieldErrors.keys); assertTrue(repository.searches.isEmpty())
    }
    @Test fun `pagination uses server total and stops at boundaries`() = test {
        login(); runCurrent(); repository.published = { ApiResult.Success(Page(listOf(repository.record.summary), 21, it.page, it.size)) }
        vm.onShown(DiscoveryView.SEARCH); runCurrent(); vm.previousPage(); vm.nextPage(); runCurrent(); vm.nextPage(); runCurrent()
        assertEquals(listOf(0, 1), repository.publishedCalls.map { it.page }); assertFalse(vm.ui.value.results.hasNext)
        vm.previousPage(); runCurrent(); assertEquals(0, vm.ui.value.results.page!!.page)
    }
    @Test fun `empty success is different from network failure and retry preserves failed page`() = test {
        login(); runCurrent(); repository.published = { CampaignFixtures.failure() }; vm.onShown(DiscoveryView.SEARCH); runCurrent()
        assertNull(vm.ui.value.results.page); assertNotNull(vm.ui.value.results.failure)
        repository.published = { ApiResult.Success(Page(emptyList(), 0, it.page, it.size)) }; vm.retrySearch(); runCurrent()
        assertNotNull(vm.ui.value.results.page); assertNull(vm.ui.value.results.failure); assertEquals(0L, vm.ui.value.results.page!!.total)
    }
    @Test fun `late uncancellable query cannot replace newer filters`() = test {
        login(); runCurrent(); val old = CompletableDeferred<ApiResult<Page<CampaignSummary>>>()
        repository.published = { withContext(NonCancellable) { old.await() } }
        vm.onShown(DiscoveryView.SEARCH); runCurrent(); vm.editFilters { copy(query = "nueva") }; vm.applyFilters(); runCurrent()
        old.complete(ApiResult.Success(Page(emptyList(), 0, 0, 20))); runCurrent()
        assertEquals(repository.record.summary.id, vm.ui.value.results.page!!.items.single().id); assertEquals("nueva", vm.ui.value.appliedFilters.query)
    }
    @Test fun `editing cancels an old request without labeling its response as current filters`() = test {
        login(); runCurrent(); val old = CompletableDeferred<ApiResult<Page<CampaignSummary>>>()
        repository.published = { withContext(NonCancellable) { old.await() } }
        vm.onShown(DiscoveryView.SEARCH); runCurrent(); vm.editFilters { copy(location = "Cusco") }
        old.complete(ApiResult.Success(Page(listOf(repository.record.summary), 1, 0, 20))); runCurrent()
        assertNull(vm.ui.value.results.page); assertTrue(vm.ui.value.pendingFilters); assertFalse(vm.ui.value.results.loading)
    }
    @Test fun `malformed page wrong index size duplicates draft or false availability is rejected`() = test {
        login(); runCurrent()
        val invalid = listOf(Page(listOf(repository.record.summary), 1, 1, 20), Page(listOf(repository.record.summary), 1, 0, 3),
            Page(listOf(repository.record.summary, repository.record.summary), 2, 0, 20),
            Page(listOf(repository.record.summary.copy(status = CampaignStatus.DRAFT)), 1, 0, 20),
            Page(listOf(repository.record.summary.copy(acceptsApplications = false)), 1, 0, 20))
        for (page in invalid) { repository.published = { ApiResult.Success(page) }; vm.refreshSearch(); runCurrent(); assertEquals(FailureKind.MALFORMED_RESPONSE, vm.ui.value.results.failure!!.kind) }
    }
    @Test fun `opening details uses clicked UUID not a demo integer or first item`() = test {
        login(); runCurrent(); val id = UUID.randomUUID(); vm.openDetails(id); runCurrent()
        assertEquals(listOf(id), repository.detailCalls); assertEquals(id, vm.ui.value.detail.details!!.summary.id)
        assertTrue(vm.ui.value.navigateToDetail); vm.consumeNavigation(); assertFalse(vm.ui.value.navigateToDetail)
    }
    @Test fun `closed and expired details remain readable with real conditions`() = test {
        login(); runCurrent()
        for (status in listOf(CampaignStatus.OPEN, CampaignStatus.CLOSED, CampaignStatus.CANCELLED)) {
            repository.details = { ApiResult.Success(repository.record.copy(summary = repository.record.summary.copy(id = it, status = status,
                acceptsApplications = false, applicationDeadline = clock.instant().minusSeconds(1)))) }
            vm.openDetails(repository.record.summary.id); runCurrent()
            assertNotNull(vm.ui.value.detail.details); assertNull(vm.ui.value.detail.failure); assertNotEquals(CampaignAvailability.AVAILABLE, vm.ui.value.detail.details!!.summary.availability(clock.instant()))
        }
    }
    @Test fun `404 and forbidden detail never substitute sample data`() = test {
        login(); runCurrent()
        for (kind in listOf(FailureKind.NOT_FOUND, FailureKind.FORBIDDEN)) { repository.details = { CampaignFixtures.failure(kind) }; vm.openDetails(UUID.randomUUID()); runCurrent()
            assertNull(vm.ui.value.detail.details); assertEquals(kind, vm.ui.value.detail.failure!!.kind) }
    }
    @Test fun `mismatched UUID draft and unpublished detail are rejected`() = test {
        login(); runCurrent()
        val invalid = listOf(repository.record.copy(summary = repository.record.summary.copy(id = UUID.randomUUID())),
            repository.record.copy(summary = repository.record.summary.copy(status = CampaignStatus.DRAFT)), repository.record.copy(publicationDate = null),
            repository.record.copy(requirements = repository.record.requirements + repository.record.requirements),
            repository.record.copy(deliverables = repository.record.deliverables + repository.record.deliverables))
        for (detail in invalid) { repository.details = { ApiResult.Success(detail) }; vm.openDetails(repository.record.summary.id); runCurrent(); assertEquals(FailureKind.MALFORMED_RESPONSE, vm.ui.value.detail.failure!!.kind) }
    }
    @Test fun `failed refresh clears previously verified detail availability`() = test {
        login(); runCurrent(); vm.openDetails(repository.record.summary.id); runCurrent(); assertNotNull(vm.ui.value.detail.details)
        repository.details = { CampaignFixtures.failure() }; vm.refreshDetail(); runCurrent()
        assertNull(vm.ui.value.detail.details); assertNotNull(vm.ui.value.detail.failure)
    }
    @Test fun `late previous detail cannot replace a more recently selected campaign`() = test {
        login(); runCurrent(); val oldId = UUID.randomUUID(); val newId = UUID.randomUUID(); val old = CompletableDeferred<ApiResult<CampaignDetails>>()
        repository.details = { id -> if (id == oldId) withContext(NonCancellable) { old.await() } else ApiResult.Success(repository.record.copy(summary = repository.record.summary.copy(id = id))) }
        vm.openDetails(oldId); runCurrent(); vm.openDetails(newId); runCurrent()
        old.complete(ApiResult.Success(repository.record.copy(summary = repository.record.summary.copy(id = oldId)))); runCurrent()
        assertEquals(newId, vm.ui.value.detail.id); assertEquals(newId, vm.ui.value.detail.details!!.summary.id)
    }
    @Test fun `logout clears private queries details navigation and late responses`() = test {
        login(); runCurrent(); val old = CompletableDeferred<ApiResult<CampaignDetails>>()
        repository.details = { withContext(NonCancellable) { old.await() } }; vm.openDetails(repository.record.summary.id); runCurrent()
        authentication.signOut(); runCurrent(); old.complete(ApiResult.Success(repository.record)); runCurrent()
        assertNull(vm.ui.value.ownerId); assertNull(vm.ui.value.detail.details); assertFalse(vm.ui.value.navigateToDetail)
    }
    @Test fun `another account or fresh login cannot inherit previous filters results and detail`() = test {
        login(); runCurrent(); vm.editFilters { copy(query = "privada") }; vm.applyFilters(); vm.openDetails(repository.record.summary.id); runCurrent()
        val other = AuthFixtures.session.copy(account = AuthFixtures.account.copy(accountId = UUID.randomUUID(), profileId = UUID.randomUUID()))
        identity.loginResult = ApiResult.Success(other); login(); runCurrent()
        assertEquals(other.account.accountId, vm.ui.value.ownerId); assertTrue(vm.ui.value.filters.isEmpty); assertNull(vm.ui.value.detail.id); assertNull(vm.ui.value.results.page)
        identity.loginResult = ApiResult.Success(other.copy(expiresAt = other.expiresAt.plusSeconds(60))); login(); runCurrent()
        assertEquals(other.expiresAt.plusSeconds(60), vm.ui.value.expiresAt)
    }
    @Test fun `resume refreshes only visible screen and hidden screens do not make requests`() = test {
        login(); runCurrent(); vm.onShown(DiscoveryView.SEARCH); runCurrent(); vm.onResume(); runCurrent()
        assertEquals(2, repository.publishedCalls.size); vm.onShown(null); vm.onResume(); runCurrent(); assertEquals(2, repository.publishedCalls.size)
        vm.openDetails(repository.record.summary.id); runCurrent(); vm.onShown(DiscoveryView.DETAIL); vm.onResume(); runCurrent(); assertEquals(2, repository.detailCalls.size)
    }
    @Test fun `deadline clock turns local availability off without manufacturing server availability`() = test {
        login(); runCurrent()
        repository.details = { ApiResult.Success(repository.record.copy(summary = repository.record.summary.copy(applicationDeadline = clock.instant().plusSeconds(1)))) }
        vm.openDetails(repository.record.summary.id); runCurrent(); vm.onShown(DiscoveryView.DETAIL); runCurrent()
        assertEquals(CampaignAvailability.AVAILABLE, vm.ui.value.detail.details!!.summary.availability(vm.ui.value.now))
        clock.time = vm.ui.value.detail.details!!.summary.applicationDeadline!!; advanceTimeBy(1001); runCurrent()
        assertEquals(CampaignAvailability.EXPIRED, vm.ui.value.detail.details!!.summary.availability(vm.ui.value.now)); assertEquals(1, repository.detailCalls.size)
    }
    @Test fun `expired session cannot start discovery requests`() = test {
        login(); runCurrent(); clock.time = AuthFixtures.session.expiresAt; vm.onShown(DiscoveryView.SEARCH); vm.openDetails(UUID.randomUUID()); runCurrent()
        assertTrue(repository.publishedCalls.isEmpty()); assertTrue(repository.detailCalls.isEmpty())
    }
    @Test fun `failed next page retries that page with the same applied filters`() = test {
        login(); runCurrent()
        repository.search = { _, request -> if (request.page == 0) ApiResult.Success(Page(listOf(repository.record.summary), 21, 0, request.size)) else CampaignFixtures.failure() }
        vm.editFilters { copy(category = "Moda") }; vm.applyFilters(); runCurrent(); vm.nextPage(); runCurrent()
        assertEquals(1, vm.ui.value.results.requestedPage); assertNull(vm.ui.value.results.page)
        repository.search = { _, request -> ApiResult.Success(Page(listOf(repository.record.summary), 21, request.page, request.size)) }
        vm.retrySearch(); runCurrent()
        assertEquals(listOf(0, 1, 1), repository.searches.map { it.second.page }); assertTrue(repository.searches.all { it.first.category == "Moda" })
    }
    @Test fun `home opportunities are independent from the exploration filters`() = test {
        login(); runCurrent(); vm.editFilters { copy(query = "consulta filtrada") }; vm.applyFilters(); runCurrent()
        vm.onShown(DiscoveryView.HOME); runCurrent()
        assertEquals(1, repository.searches.size); assertEquals(PageRequest(0, 3), repository.publishedCalls.single())
        assertEquals("consulta filtrada", vm.ui.value.appliedFilters.query)
    }
    @Test fun `background completion does not restart the clock until resume`() = test {
        login(); runCurrent(); val reply = CompletableDeferred<ApiResult<CampaignDetails>>()
        repository.details = { reply.await() }; vm.openDetails(repository.record.summary.id); vm.onShown(DiscoveryView.DETAIL); runCurrent()
        vm.onBackground(); reply.complete(ApiResult.Success(repository.record)); runCurrent()
        val previous = vm.ui.value.now; clock.time = clock.time.plusSeconds(30); advanceTimeBy(30_001); runCurrent()
        assertEquals(previous, vm.ui.value.now)
        vm.onResume(); runCurrent(); assertEquals(clock.instant(), vm.ui.value.now)
    }
}
