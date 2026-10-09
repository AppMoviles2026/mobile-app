package com.example.collabpro.campaign

import androidx.lifecycle.ViewModelStore
import com.example.collabpro.auth.*
import com.example.collabpro.core.application.security.ExpectedAccount
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.dashboard.LoadOwnActivityTotal
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.presentation.dashboard.*
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityDashboardTest {
    @get:Rule val main = MainDispatcherRule()
    private val identity = FakeIdentity()
    private val clock = MutableClock()
    private val authentication = AuthenticationSession(identity, MemorySessions(), clock)
    private val campaigns = FakeCampaigns()
    private val applications = FakeApplications()
    private val totals = LoadOwnActivityTotal(campaigns, applications)
    private lateinit var vm: ActivityDashboardViewModel
    private val store = ViewModelStore()
    @Before fun setup() { vm = ActivityDashboardViewModel(totals, authentication, clock); store.put("dashboard", vm) }
    @After fun cleanup() { store.clear() }
    private suspend fun TestScope.login(type: AccountType = AccountType.CREATOR) {
        identity.loginResult = ApiResult.Success(AuthFixtures.session.copy(account = AuthFixtures.account.copy(accountType = type)))
        authentication.signIn("dashboard@example.test", "test-only"); runCurrent()
    }
    @Test fun `a creator dashboard uses global server total not number of items or pending count`() = runTest(main.dispatcher) {
        login(); applications.mineCall = { ApiResult.Success(Page(listOf(ownApplication().copy(status = ApplicationStatus.CANCELLED)), 103, 0, 1)) }
        vm.onShown(true); runCurrent(); assertEquals(103L, vm.ui.value.total); assertEquals(AccountType.CREATOR, vm.ui.value.type)
        assertEquals(PageRequest(0, 1), applications.pages.single())
        assertEquals(AuthFixtures.account.accountId, applications.actors.single()!!.accountId)
        assertEquals(AuthFixtures.session.expiresAt, applications.actors.single()!!.expiresAt)
    }
    @Test fun `brand summary includes drafts as total campaigns without requesting applications`() = runTest(main.dispatcher) {
        login(AccountType.BRAND); repeat(3) { campaigns.create(NewCampaign("Título", "Objetivo", null, "Moda", "Jóvenes", null), IdempotencyKey()) }
        vm.onShown(true); runCurrent(); assertEquals(3L, vm.ui.value.total); assertTrue(applications.pages.isEmpty())
    }
    @Test fun `zero total is a successful empty state`() = runTest(main.dispatcher) {
        login(); vm.onShown(true); runCurrent(); assertEquals(0L, vm.ui.value.total); assertNull(vm.ui.value.failure)
    }
    @Test fun `a network failure does not become zero or retain a misleading total`() = runTest(main.dispatcher) {
        login(); vm.onShown(true); runCurrent()
        applications.mineCall = { CampaignFixtures.failure() }; vm.refresh(); runCurrent()
        assertNull(vm.ui.value.total); assertEquals(FailureKind.NETWORK, vm.ui.value.failure!!.kind)
        applications.mineCall = null; vm.refresh(); runCurrent(); assertEquals(0L, vm.ui.value.total)
    }
    @Test fun `another profile data is not accepted even when account UUID is known`() = runTest(main.dispatcher) {
        applications.mineCall = { ApiResult.Success(Page(listOf(ownApplication().copy(creatorId = AuthFixtures.account.accountId)), 1, 0, 1)) }
        assertEquals(FailureKind.MALFORMED_RESPONSE, (totals.applications(AuthFixtures.account.profileId) as ApiResult.Failure).error.kind)
    }
    @Test fun `incomplete invalid or unexpected page cannot produce a total`() = runTest(main.dispatcher) {
        for (page in listOf(Page(emptyList(), 1, 0, 1), Page(listOf(ownApplication()), 1, 1, 1), Page(listOf(ownApplication()), 1, 0, 2))) {
            applications.mineCall = { ApiResult.Success(page) }
            assertTrue(totals.applications(AuthFixtures.account.profileId) is ApiResult.Failure)
        }
    }
    @Test fun `brand ownership is validated for summary`() = runTest(main.dispatcher) {
        campaigns.create(NewCampaign("Título", "Objetivo", null, "Moda", "Jóvenes", null), IdempotencyKey())
        assertTrue(totals.campaigns(UUID.randomUUID()) is ApiResult.Failure)
    }
    @Test fun `unverified and expired sessions do not request a summary`() = runTest(main.dispatcher) {
        runCurrent(); vm.onShown(true); runCurrent(); assertTrue(applications.pages.isEmpty())
        login(); clock.time = AuthFixtures.session.expiresAt; vm.refresh(); runCurrent(); assertTrue(applications.pages.isEmpty())
    }
    @Test fun `refresh on resume is restricted to visible home`() = runTest(main.dispatcher) {
        login(); vm.onResume(); runCurrent(); assertTrue(applications.pages.isEmpty())
        vm.onShown(true); runCurrent(); vm.onResume(); runCurrent(); assertEquals(2, applications.pages.size)
        vm.onShown(false); vm.onResume(); runCurrent(); assertEquals(2, applications.pages.size)
    }
    @Test fun `late response cannot restore summary after logout`() = runTest(main.dispatcher) {
        login(); val reply = CompletableDeferred<ApiResult<Page<Application>>>()
        applications.mineCall = { withContext(NonCancellable) { reply.await() } }; vm.onShown(true); runCurrent()
        authentication.signOut(); runCurrent(); reply.complete(ApiResult.Success(Page(listOf(ownApplication()), 10, 0, 1))); runCurrent()
        assertEquals(ActivityDashboardUiState(), vm.ui.value)
    }
    @Test fun `out of order refresh cannot replace newest confirmed count`() = runTest(main.dispatcher) {
        login(); val reply = CompletableDeferred<ApiResult<Page<Application>>>()
        applications.mineCall = { withContext(NonCancellable) { reply.await() } }; vm.onShown(true); runCurrent()
        applications.mineCall = { ApiResult.Success(Page(listOf(ownApplication()), 2, 0, 1)) }; vm.refresh(); runCurrent()
        reply.complete(ApiResult.Success(Page(listOf(ownApplication()), 1, 0, 1))); runCurrent(); assertEquals(2L, vm.ui.value.total)
    }
}
