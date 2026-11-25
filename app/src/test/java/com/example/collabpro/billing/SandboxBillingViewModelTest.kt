package com.example.collabpro.billing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.collabpro.auth.*
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.billing.application.*
import com.example.collabpro.features.billing.domain.model.*
import com.example.collabpro.features.billing.domain.repositories.BillingRepository
import com.example.collabpro.features.billing.presentation.sandbox.*
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.domain.model.AccountType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SandboxBillingViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val clock = MutableClock()
    private val storage = SavedStateHandle()
    private val identity = FakeIdentity()
    private val brand = AuthFixtures.account.copy(accountType = AccountType.BRAND)
    private val auth = AuthenticationSession(identity, MemorySessions(), clock)
    private val owners = ViewModelStore()
    private val repository = BillingFake()
    private lateinit var vm: SandboxBillingViewModel

    @Before fun setup() {
        identity.loginResult = ApiResult.Success(AuthFixtures.session.copy(account = brand))
        vm = SandboxBillingViewModel(repository, auth, storage, clock).also { owners.put("billing", it) }
    }

    @After fun cleanup() { owners.clear() }

    @Test fun `browser return only reconciles once even through recreation until company confirms`() = runTest(main.dispatcher) {
        auth.signIn("company@example.test", "pass")
        val destination = BillingDestination(BillingTab.COMPENSATION, repository.collaboration)
        vm.open(destination)
        runCurrent()
        val original = vm.ui.value.compensation!!
        vm.approveInBrowser(PayPalReturnKind.COMPENSATION, original.collaborationId, original.approvalUrl!!) { true }
        owners.clear()
        vm = SandboxBillingViewModel(repository, auth, storage, clock).also { owners.put("billing", it) }
        val returnUrl = "collabpro://compensation-return?collaborationId=${original.collaborationId}&status=PAID"
        vm.receiveReturn("collabpro://compensation-return?collaborationId=${UUID.randomUUID()}&status=PAID")
        vm.receiveReturn(returnUrl)
        vm.receiveReturn(returnUrl)
        runCurrent()
        assertEquals(1, repository.refreshes)
        assertTrue(repository.confirmations.isEmpty())
        assertEquals(destination, vm.ui.value.navigation)
        assertFalse(vm.ui.value.compensation!!.wasPaid)
        assertEquals(8, vm.ui.value.compensation!!.version.toInt())
        vm.confirmPayment(original)
        runCurrent()
        assertTrue(repository.confirmations.isEmpty())
        vm.confirmPayment(vm.ui.value.compensation!!)
        runCurrent()
        assertEquals(8L, repository.confirmations.single().expectedVersion)
        assertFalse(vm.ui.value.compensation!!.wasPaid)
        assertEquals(clock.instant(), vm.ui.value.compensation!!.fundedAt)
    }

    @Test fun `uncertain capture retries same command with same key after recreation`() = runTest(main.dispatcher) {
        auth.signIn("company@example.test", "pass")
        val destination = BillingDestination(BillingTab.COMPENSATION, repository.collaboration)
        vm.open(destination)
        runCurrent()
        repository.confirmFailure = ApiResult.Failure(ApiFailure(FailureKind.TIMEOUT, message = "Timeout"))
        vm.confirmPayment(vm.ui.value.compensation!!)
        runCurrent()
        assertTrue(vm.ui.value.recovering)
        val first = repository.confirmations.single()
        owners.clear()
        vm = SandboxBillingViewModel(repository, auth, storage, clock).also { owners.put("billing", it) }
        vm.open(destination)
        runCurrent()
        assertTrue(vm.ui.value.recovering)
        repository.confirmFailure = null
        vm.retryPending()
        runCurrent()
        assertEquals(listOf(first, first), repository.confirmations)
        assertFalse(vm.ui.value.recovering)
    }

    @Test fun `switching accounts invalidates ticket and creator cannot capture`() = runTest(main.dispatcher) {
        auth.signIn("company@example.test", "pass")
        val destination = BillingDestination(BillingTab.COMPENSATION, repository.collaboration)
        vm.open(destination)
        runCurrent()
        val record = vm.ui.value.compensation!!
        vm.approveInBrowser(PayPalReturnKind.COMPENSATION, record.collaborationId, record.approvalUrl!!) { true }
        auth.signOut()
        identity.loginResult = ApiResult.Success(AuthFixtures.session.copy(account = AuthFixtures.account.copy(accountId = UUID.randomUUID())))
        auth.signIn("creator@example.test", "pass")
        runCurrent()
        vm.receiveReturn("collabpro://compensation-return?collaborationId=${record.collaborationId}&status=PAID")
        vm.open(destination)
        runCurrent()
        vm.confirmPayment(vm.ui.value.compensation!!)
        runCurrent()
        assertTrue(repository.confirmations.isEmpty())
        assertEquals(0, repository.refreshes)
    }

    @Test fun `Sandbox links reject live origin malformed return and mismatched identifiers`() {
        val id = repository.collaboration
        val base = "collabpro://compensation-return"
        assertNotNull(PayPalSandboxLinks.approval(repository.compensation.approvalUrl))
        assertNull(PayPalSandboxLinks.approval("https://www.paypal.com/checkoutnow?token=opaque"))
        assertNull(PayPalSandboxLinks.approval("https://www.sandbox.paypal.com.attacker.test/"))
        assertNull(PayPalSandboxLinks.approval("https://user@www.sandbox.paypal.com/"))
        assertNull(PayPalSandboxLinks.returned("$base?collaborationId=$id&collaborationId=$id"))
        assertNull(PayPalSandboxLinks.returned("$base?authorizationId=$id"))
        assertNull(PayPalSandboxLinks.returned("$base?collaborationId=1-1-1-1-1"))
        assertNull(PayPalSandboxLinks.returned("$base?collaborationId=$id&code=secret"))
        assertEquals(PayPalReturn(PayPalReturnKind.COMPENSATION, id),
            PayPalSandboxLinks.returned("$base?collaborationId=$id&status=PAID"))
    }
}

private class BillingFake : BillingRepository {
    val collaboration: UUID = UUID.randomUUID()
    val compensation = BillingCompensation(UUID.randomUUID(), collaboration, UUID.randomUUID(), "CASH",
        BigDecimal("5.00"), "USD", "Contenido aprobado", "AWAITING_COMPANY_APPROVAL", true, true, 0, false,
        null, null, null, null, "PAYPAL", "SANDBOX", "https://www.sandbox.paypal.com/checkoutnow?token=opaque",
        null, null, 7, listOf("CONFIRM_FUNDING"))
    val confirmations = mutableListOf<ConfirmCompensation>()
    var confirmFailure: ApiResult.Failure? = null
    var refreshes = 0

    override suspend fun compensation(id: UUID) = ApiResult.Success(compensation)
    override suspend fun refreshPayment(id: UUID): ApiResult<BillingCompensation> {
        refreshes++
        return ApiResult.Success(compensation.copy(version = 8))
    }
    override suspend fun confirmPayment(id: UUID, body: ConfirmCompensation): ApiResult<BillingCompensation> {
        confirmations += body
        return confirmFailure ?: ApiResult.Success(compensation.copy(status = "PAYOUT_PENDING", version = body.expectedVersion + 1,
            fundedAt = AuthFixtures.now, approvalUrl = null, availableActions = emptyList()))
    }
    override suspend fun methods(id: UUID, page: PageRequest): ApiResult<Page<BillingMethod>> = ApiResult.Success(empty(page))
    override suspend fun compensationHistory(id: UUID, page: PageRequest): ApiResult<Page<CompensationAudit>> = ApiResult.Success(empty(page))
    override suspend fun beginLink(id: UUID, body: BeginBilling): ApiResult<BillingAuthorization> = unexpected()
    override suspend fun authorization(id: UUID): ApiResult<BillingAuthorization> = unexpected()
    override suspend fun cancelLink(id: UUID): ApiResult<BillingAuthorization> = unexpected()
    override suspend fun validateMethod(id: UUID, body: ValidateBillingMethod): ApiResult<BillingReadiness> = unexpected()
    override suspend fun plans(): ApiResult<List<SubscriptionPlan>> = unexpected()
    override suspend fun beginSubscription(id: UUID, body: BeginSubscription): ApiResult<BillingSubscription> = unexpected()
    override suspend fun currentSubscription(id: UUID): ApiResult<BillingSubscription> = unexpected()
    override suspend fun subscriptionHistory(id: UUID, page: PageRequest): ApiResult<Page<BillingSubscription>> = unexpected()
    override suspend fun subscription(id: UUID): ApiResult<BillingSubscription> = unexpected()
    override suspend fun refreshSubscription(id: UUID): ApiResult<BillingSubscription> = unexpected()
    override suspend fun resumeSubscription(id: UUID, body: ResumeSubscription): ApiResult<BillingSubscription> = unexpected()
    override suspend fun cancelSubscription(id: UUID): ApiResult<BillingSubscription> = unexpected()
    override suspend fun compensationById(id: UUID): ApiResult<BillingCompensation> = unexpected()
    override suspend fun beginPayment(id: UUID, body: BeginCompensation): ApiResult<BillingCompensation> = unexpected()

    private fun unexpected(): Nothing = error("Unexpected billing request")
    private fun <T> empty(page: PageRequest): Page<T> = Page(emptyList(), 0L, page.page, page.size)
}
