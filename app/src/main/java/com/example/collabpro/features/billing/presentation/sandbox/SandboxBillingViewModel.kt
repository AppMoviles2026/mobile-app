package com.example.collabpro.features.billing.presentation.sandbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.collabpro.core.application.security.ExpectedAccount
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.billing.application.*
import com.example.collabpro.features.billing.domain.model.*
import com.example.collabpro.features.billing.domain.repositories.BillingRepository
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.application.auth.SessionState
import com.example.collabpro.features.identity.domain.model.AccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SandboxBillingViewModel @Inject constructor(
    private val repository: BillingRepository,
    private val auth: AuthenticationSession,
    private val saved: SavedStateHandle,
    private val clock: Clock
) : ViewModel() {
    private val mutableUi = MutableStateFlow(BillingState())
    internal val ui = mutableUi.asStateFlow()
    private var owner: BillingOwner? = null
    private var job: Job? = null
    private var queuedReturn: PayPalReturn? = null
    private var pending = PendingBillingCommand.decode(saved.get<ArrayList<String>>(COMMAND))
    private var ticket = BrowserReturnTicket.decode(saved.get<ArrayList<String>>(BROWSER))
    private var trackedLink = BrowserReturnTicket.decode(saved.get<ArrayList<String>>(LINK))
    private var queuedDestination: BillingDestination? = null
    private var ignoreNextRefresh: BillingDestination? = null

    init {
        viewModelScope.launch { auth.state.collect { synchronize(); drainReturn() } }
    }

    private fun session() = (auth.state.value as? SessionState.Authenticated)
        ?.takeIf { it.expiresAt.isAfter(clock.instant()) }
    private fun scope() = session()?.let { BillingOwner(it.account.accountId, it.expiresAt) }
    private fun brand() = session()?.account?.accountType == AccountType.BRAND
    private fun purpose() = if (brand()) BillingPurpose.PAYMENT else BillingPurpose.PAYOUT

    private fun synchronize(): BillingOwner? {
        val active = scope()
        if (active != owner) {
            job?.cancel()
            owner = active
            ignoreNextRefresh = null
            queuedDestination = null
            mutableUi.value = BillingState(owner = active?.id, expiresAt = active?.expiry,
                recovering = active != null && pending?.owner == active)
        }
        if (auth.state.value == SessionState.Restoring || auth.state.value is SessionState.VerificationFailed) return null
        if (pending != null && pending?.owner != active) setPending(null)
        if (ticket != null && ticket?.owner != active) setTicket(null)
        if (trackedLink != null && trackedLink?.owner != active) setLink(null)
        if (ticket == null) queuedReturn = null
        return active
    }

    private fun setPending(value: PendingBillingCommand?) {
        pending = value
        saved[COMMAND] = value?.encode()
        mutableUi.value = ui.value.copy(recovering = value != null)
    }
    private fun setTicket(value: BrowserReturnTicket?) { ticket = value; saved[BROWSER] = value?.encode() }
    private fun setLink(value: BrowserReturnTicket?) { trackedLink = value; saved[LINK] = value?.encode() }

    internal fun open(destination: BillingDestination) {
        synchronize() ?: return
        if (destination.tab == BillingTab.SUBSCRIPTION && !brand()) return
        if (ignoreNextRefresh == destination) {
            ignoreNextRefresh = null
            return
        }
        if (ui.value.loading) {
            if (ui.value.destination != destination) queuedDestination = destination
            return
        }
        if (ui.value.destination != destination) {
            mutableUi.value = ui.value.copy(
                destination = destination, loading = false, failure = null, notice = null,
                historicalSubscription = null, compensation = ui.value.compensation?.takeIf { it.collaborationId == destination.collaborationId },
                audit = if (ui.value.destination?.collaborationId == destination.collaborationId) ui.value.audit else null
            )
        }
        load(reconcile = false)
    }

    internal fun consumeNavigation() { mutableUi.value = ui.value.copy(navigation = null) }

    internal fun refresh() = load(reconcile = true)

    private fun load(reconcile: Boolean) {
        val destination = ui.value.destination ?: return
        runForOwner { active ->
            when (destination.tab) {
                BillingTab.METHODS -> {
                    trackedLink?.id?.let { id ->
                        consume(active, repository.authorization(id)) { saveAuthorization(active, it) }
                    }
                    loadMethods(active, ui.value.methods?.page ?: 0)
                }
                BillingTab.SUBSCRIPTION -> {
                    if (!consume(active, repository.currentSubscription(active.id), ::saveSubscription) && scope() == active) {
                        mutableUi.value = ui.value.copy(subscription = null)
                    }
                    consume(active, repository.plans()) { mutableUi.value = ui.value.copy(plans = it) }
                    loadUsable(active)
                    loadSubscriptionHistory(active, ui.value.subscriptions?.page ?: 0)
                }
                BillingTab.COMPENSATION -> {
                    val id = destination.collaborationId ?: return@runForOwner
                    val response = if (reconcile) repository.refreshPayment(id) else repository.compensation(id)
                    val loaded = consume(active, response) { mutableUi.value = ui.value.copy(compensation = it) }
                    if (!loaded && scope() == active) mutableUi.value = ui.value.copy(compensation = null)
                    if (brand()) loadUsable(active)
                    loadAudit(active, id, ui.value.audit?.page ?: 0)
                }
            }
        }
    }

    internal fun methodsPage(page: Int) = runForOwner { loadMethods(it, page) }
    internal fun subscriptionsPage(page: Int) = runForOwner { if (brand()) loadSubscriptionHistory(it, page) }
    internal fun compensationPage(page: Int) = runForOwner { active ->
        ui.value.destination?.collaborationId?.let { loadAudit(active, it, page) }
    }

    private suspend fun loadMethods(active: BillingOwner, page: Int) {
        consume(active, repository.methods(active.id, PageRequest(page))) { result ->
            mutableUi.value = ui.value.copy(methods = result)
        }
    }

    private suspend fun loadUsable(active: BillingOwner) {
        val result = mutableListOf<BillingMethod>()
        var page = 0
        while (scope() == active) {
            when (val response = repository.methods(active.id, PageRequest(page, 100))) {
                is ApiResult.Success -> {
                    val data = response.value
                    result += data.items.filter { it.purpose == BillingPurpose.PAYMENT && it.status == "ACTIVE" }
                    page++
                    if (data.items.isEmpty() || page.toLong() * data.size >= data.total) break
                }
                is ApiResult.Failure -> {
                    consume(active, response) {}
                    if (scope() == active) mutableUi.value = ui.value.copy(usable = emptyList())
                    return
                }
            }
        }
        if (scope() == active) mutableUi.value = ui.value.copy(usable = result.distinctBy { it.methodId })
    }

    private suspend fun loadSubscriptionHistory(active: BillingOwner, page: Int) {
        consume(active, repository.subscriptionHistory(active.id, PageRequest(page))) {
            mutableUi.value = ui.value.copy(subscriptions = it)
        }
    }

    private suspend fun loadAudit(active: BillingOwner, id: UUID, page: Int) {
        consume(active, repository.compensationHistory(id, PageRequest(page))) { mutableUi.value = ui.value.copy(audit = it) }
    }

    internal fun validateMethod(method: BillingMethod) = runForOwner { active ->
        if (method.purpose == purpose() && method.status == "ACTIVE") {
            val result = repository.validateMethod(active.id, ValidateBillingMethod(method.methodId))
            consume(active, result) {
                if (it.purpose == purpose()) mutableUi.value = ui.value.copy(notice = "Medio verificado para tu cuenta.")
                else fail(active, "El medio no sirve para esta operación.", "METHOD_REQUIRED")
            }
        }
    }

    internal fun beginLink() {
        val active = synchronize() ?: return
        if (ui.value.authorization?.terminal == false) return
        start(PendingBillingCommand(active, BillingAction.LINK, active.id))
    }

    internal fun cancelLink(record: BillingAuthorization) = runForOwner { active ->
        if (record.terminal || ui.value.authorization != record || ui.value.recovering) return@runForOwner
        consume(active, repository.cancelLink(record.authorizationId)) { saveAuthorization(active, it) }
    }

    internal fun subscribe(plan: SubscriptionPlan, method: BillingMethod?) {
        val active = synchronize() ?: return
        if (!brand() || ui.value.destination?.tab != BillingTab.SUBSCRIPTION || !plan.available ||
            plan !in ui.value.plans.orEmpty() || ui.value.subscription?.canStart != true) return
        if (method == null || method !in ui.value.usable) {
            fail(active, "Vincula y elige un medio de pago de empresa.", "METHOD_REQUIRED")
            return
        }
        start(PendingBillingCommand(active, BillingAction.SUBSCRIBE, active.id, method = method.methodId, plan = plan.planId))
    }

    internal fun inspectSubscription(id: UUID) = runForOwner { active ->
        if (brand()) consume(active, repository.subscription(id)) { mutableUi.value = ui.value.copy(historicalSubscription = it) }
    }
    internal fun closeSubscriptionDetail() { mutableUi.value = ui.value.copy(historicalSubscription = null) }

    internal fun refreshSubscription(record: BillingSubscription) = runForOwner { active ->
        if (!brand() || ui.value.subscription != record) return@runForOwner
        record.subscriptionId?.let { id ->
            consume(active, repository.refreshSubscription(id), ::saveSubscription)
            if (scope() == active) loadSubscriptionHistory(active, ui.value.subscriptions?.page ?: 0)
        }
    }

    internal fun resumeSubscription(record: BillingSubscription) = runForOwner { active ->
        if (!brand() || ui.value.subscription != record || ui.value.recovering || record.canStart) return@runForOwner
        record.subscriptionId?.let { id ->
            consume(active, repository.resumeSubscription(id, ResumeSubscription(record.version)), ::saveSubscription)
        }
    }

    internal fun cancelSubscription(record: BillingSubscription) = runForOwner { active ->
        if (!brand() || ui.value.subscription != record || ui.value.recovering || record.canStart) return@runForOwner
        record.subscriptionId?.let { id ->
            consume(active, repository.cancelSubscription(id), ::saveSubscription)
            if (scope() == active) loadSubscriptionHistory(active, ui.value.subscriptions?.page ?: 0)
        }
    }

    internal fun beginPayment(record: BillingCompensation, method: BillingMethod?) {
        val active = synchronize() ?: return
        if (!brand() || !canPay(record, "INITIATE_PAYMENT")) return
        if (record.fundedAt == null && (method == null || method !in ui.value.usable)) {
            fail(active, "Vincula y elige un medio de pago de empresa.", "METHOD_REQUIRED")
            return
        }
        start(PendingBillingCommand(active, BillingAction.FUND, record.collaborationId,
            method = if (record.fundedAt == null) method?.methodId else null, version = record.version))
    }

    internal fun confirmPayment(record: BillingCompensation) {
        val active = synchronize() ?: return
        if (!brand() || !canPay(record, "CONFIRM_FUNDING") || record.fundedAt != null) return
        start(PendingBillingCommand(active, BillingAction.CONFIRM, record.collaborationId, version = record.version))
    }

    private fun canPay(record: BillingCompensation, action: String) =
        ui.value.destination == BillingDestination(BillingTab.COMPENSATION, record.collaborationId) &&
            ui.value.compensation == record && record.paymentApplicable && record.eligible && record.openIncidentCount == 0 &&
            !record.wasPaid && record.unavailableReason == null && action in record.availableActions

    private fun start(command: PendingBillingCommand) {
        if (pending != null || ui.value.loading || scope() != command.owner) return
        setPending(command)
        execute(command)
    }

    internal fun retryPending() {
        synchronize() ?: return
        val command = pending ?: return
        val destination = when (command.action) {
            BillingAction.LINK -> BillingDestination(BillingTab.METHODS)
            BillingAction.SUBSCRIBE -> BillingDestination(BillingTab.SUBSCRIPTION)
            BillingAction.FUND, BillingAction.CONFIRM -> BillingDestination(BillingTab.COMPENSATION, command.target)
        }
        if (ui.value.destination == destination) execute(command)
        else {
            open(destination)
            mutableUi.value = ui.value.copy(navigation = destination)
        }
    }

    private fun execute(command: PendingBillingCommand) = runForOwner { active ->
        if (active != command.owner || (command.action != BillingAction.LINK && !brand())) return@runForOwner
        when (command.action) {
            BillingAction.LINK -> finish(active, command, repository.beginLink(active.id, BeginBilling(command.request))) {
                saveAuthorization(active, it)
                if (it.status == "PREPARING") it.authorizationId else null
            }
            BillingAction.SUBSCRIBE -> finish(active, command, repository.beginSubscription(active.id,
                BeginSubscription(command.request, command.plan!!, command.method!!))) {
                saveSubscription(it)
                if (it.status == "PREPARING") it.subscriptionId else null
            }
            BillingAction.FUND -> finish(active, command, repository.beginPayment(command.target,
                BeginCompensation(command.request, command.method, command.version!!))) {
                mutableUi.value = ui.value.copy(compensation = it)
                null
            }
            BillingAction.CONFIRM -> finish(active, command, repository.confirmPayment(command.target,
                ConfirmCompensation(command.request, command.version!!))) {
                mutableUi.value = ui.value.copy(compensation = it)
                null
            }
        }
        if (scope() != active || ui.value.failure != null) return@runForOwner
        when (command.action) {
            BillingAction.LINK -> if (ui.value.authorization?.status == "LINKED") loadMethods(active, 0)
            BillingAction.SUBSCRIBE -> loadSubscriptionHistory(active, 0)
            BillingAction.FUND, BillingAction.CONFIRM -> loadAudit(active, command.target, 0)
        }
    }

    private fun <T> finish(active: BillingOwner, command: PendingBillingCommand, result: ApiResult<T>, handle: (T) -> UUID?) {
        if (scope() != active) return
        when (result) {
            is ApiResult.Success -> {
                val preparingId = handle(result.value)
                setPending(preparingId?.let { command.copy(resource = it) })
            }
            is ApiResult.Failure -> {
                val status = result.error.httpStatus
                val uncertain = if (result.error.code == "PROVIDER_NOT_CONFIGURED") false else if (status == null) result.error.kind in setOf(
                    FailureKind.NETWORK, FailureKind.TIMEOUT, FailureKind.MALFORMED_RESPONSE, FailureKind.SERVER
                ) else status >= 500 || status == 408 || status == 429
                if (!uncertain) {
                    setPending(null)
                    if (command.action in setOf(BillingAction.FUND, BillingAction.CONFIRM))
                        mutableUi.value = ui.value.copy(compensation = null)
                    if (command.action == BillingAction.SUBSCRIBE)
                        mutableUi.value = ui.value.copy(subscription = null)
                }
                mutableUi.value = ui.value.copy(failure = result.error)
            }
        }
    }

    private fun saveAuthorization(active: BillingOwner, record: BillingAuthorization) {
        if (record.purpose != purpose()) {
            fail(active, "La autorización no coincide con el tipo de cuenta.", "METHOD_REQUIRED")
            return
        }
        setLink(BrowserReturnTicket(active, PayPalReturnKind.LINK, record.authorizationId))
        if (pending?.resource == record.authorizationId && record.status != "PREPARING") setPending(null)
        mutableUi.value = ui.value.copy(authorization = record)
    }

    private fun saveSubscription(record: BillingSubscription) {
        if (pending?.resource != null && pending?.resource == record.subscriptionId && record.status != "PREPARING") setPending(null)
        mutableUi.value = ui.value.copy(subscription = record)
    }

    internal fun approveInBrowser(kind: PayPalReturnKind, id: UUID, raw: String, launch: (String) -> Boolean) {
        val active = synchronize() ?: return
        val state = ui.value
        val current = when (kind) {
            PayPalReturnKind.LINK -> state.authorization?.takeIf {
                it.authorizationId == id && it.status == "PENDING_APPROVAL" && clock.instant().isBefore(it.expiresAt)
            }?.approvalUrl
            PayPalReturnKind.SUBSCRIPTION -> state.subscription?.takeIf {
                brand() && it.subscriptionId == id && it.status == "APPROVAL_PENDING" &&
                    it.consentExpiresAt?.isAfter(clock.instant()) == true
            }?.approvalUrl
            PayPalReturnKind.COMPENSATION -> state.compensation?.takeIf {
                brand() && it.collaborationId == id && it.fundedAt == null && canPay(it, "CONFIRM_FUNDING")
            }?.approvalUrl
        }
        if (state.loading || current != raw || PayPalSandboxLinks.approval(raw) == null) {
            fail(active, "La aprobación Sandbox ya no está disponible. Actualiza el estado.")
            return
        }
        val next = BrowserReturnTicket(active, kind, id)
        setTicket(next)
        if (!launch(raw)) {
            setTicket(null)
            fail(active, "No se pudo abrir PayPal Sandbox. Comprueba el navegador del dispositivo.")
        }
    }

    fun receiveReturn(raw: String) {
        val link = PayPalSandboxLinks.returned(raw) ?: return
        val expected = ticket ?: return
        if (expected.kind != link.kind || expected.id != link.id) return
        queuedReturn = link
        drainReturn()
    }

    private fun drainReturn() {
        val active = scope() ?: return
        val link = queuedReturn ?: return
        val expected = ticket ?: return
        if (ui.value.loading || expected.owner != active || expected.kind != link.kind || expected.id != link.id) return
        queuedReturn = null
        // Consume the matching browser return once. Read and explicit refresh remain available.
        setTicket(null)
        val destination = when (link.kind) {
            PayPalReturnKind.LINK -> BillingDestination(BillingTab.METHODS)
            PayPalReturnKind.SUBSCRIPTION -> BillingDestination(BillingTab.SUBSCRIPTION)
            PayPalReturnKind.COMPENSATION -> BillingDestination(BillingTab.COMPENSATION, link.id)
        }
        runForOwner { checked ->
            val verified = when (link.kind) {
                PayPalReturnKind.LINK -> consume(checked, repository.authorization(link.id)) { record ->
                    saveAuthorization(checked, record)
                }
                PayPalReturnKind.SUBSCRIPTION -> if (!brand()) false else consume(checked, repository.subscription(link.id)) {
                    saveSubscription(it)
                }
                PayPalReturnKind.COMPENSATION -> consume(checked, repository.refreshPayment(link.id)) {
                    mutableUi.value = ui.value.copy(compensation = it)
                }
            }
            if (!verified || scope() != checked) return@runForOwner
            queuedDestination = null
            mutableUi.value = ui.value.copy(destination = destination, historicalSubscription = null, audit = null)
            when (link.kind) {
                PayPalReturnKind.LINK -> loadMethods(checked, 0)
                PayPalReturnKind.SUBSCRIPTION -> {
                    consume(checked, repository.plans()) { mutableUi.value = ui.value.copy(plans = it) }
                    loadUsable(checked)
                    loadSubscriptionHistory(checked, 0)
                }
                PayPalReturnKind.COMPENSATION -> {
                    if (brand()) loadUsable(checked)
                    loadAudit(checked, link.id, 0)
                }
            }
            if (scope() != checked) return@runForOwner
            ignoreNextRefresh = destination
            mutableUi.value = ui.value.copy(navigation = destination, notice = "Estado actualizado.")
        }
    }

    private fun runForOwner(work: suspend (BillingOwner) -> Unit) {
        val active = synchronize() ?: return
        if (ui.value.loading) return
        mutableUi.value = ui.value.copy(loading = true, failure = null, notice = null)
        job = viewModelScope.launch(ExpectedAccount(active.id, active.expiry)) {
            try { work(active) }
            finally {
                if (scope() == active) {
                    mutableUi.value = ui.value.copy(loading = false)
                    drainReturn()
                    if (!ui.value.loading) queuedDestination?.let { next ->
                        queuedDestination = null
                        open(next)
                    }
                }
            }
        }
    }

    private fun <T> consume(active: BillingOwner, result: ApiResult<T>, use: (T) -> Unit): Boolean {
        if (scope() != active) return false
        return when (result) {
            is ApiResult.Success -> { use(result.value); true }
            is ApiResult.Failure -> { mutableUi.value = ui.value.copy(failure = result.error); false }
        }
    }

    private fun fail(active: BillingOwner, message: String, code: String? = null) {
        if (scope() == active) mutableUi.value = ui.value.copy(failure = ApiFailure(FailureKind.CONFLICT, code, message))
    }

    private companion object {
        const val COMMAND = "sandbox-billing-command"
        const val BROWSER = "sandbox-billing-browser"
        const val LINK = "sandbox-billing-link"
    }
}
