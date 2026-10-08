package com.example.collabpro.features.campaign.presentation.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.collabpro.core.application.security.ExpectedAccount
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.discovery.DiscoveryFilters
import com.example.collabpro.features.campaign.application.usecases.CampaignUseCases
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.identity.application.auth.*
import com.example.collabpro.features.identity.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CampaignDiscoveryViewModel @Inject constructor(private val useCases: CampaignUseCases,
    private val authentication: AuthenticationSession, private val clock: Clock) : ViewModel() {
    private val mutableUi = MutableStateFlow(CampaignDiscoveryUiState())
    val ui = mutableUi.asStateFlow()
    private var owner: UUID? = null
    private var expiry: Instant? = null
    private var generation = 0L
    private var searchRevision = 0L
    private var homeRevision = 0L
    private var detailRevision = 0L
    private var searchJob: Job? = null
    private var homeJob: Job? = null
    private var detailJob: Job? = null
    private var clockJob: Job? = null
    private var visible: DiscoveryView? = null
    private var foreground = true

    init { viewModelScope.launch { authentication.state.collect { state ->
        val current = (state as? SessionState.Authenticated)?.takeIf { it.account.accountType == AccountType.CREATOR && it.account.status == AccountStatus.ACTIVE }
        if (owner != current?.account?.accountId || expiry != current?.expiresAt) {
            cancelAll(); owner = current?.account?.accountId; expiry = current?.expiresAt; visible = null
            mutableUi.value = CampaignDiscoveryUiState(ownerId = owner, expiresAt = expiry, now = clock.instant())
        }
    } } }
    private fun cancelAll() {
        generation++; searchRevision++; homeRevision++; detailRevision++
        searchJob?.cancel(); homeJob?.cancel(); detailJob?.cancel(); clockJob?.cancel()
    }
    private fun active(revision: Long = generation): Boolean {
        val state = authentication.state.value as? SessionState.Authenticated ?: return false
        return owner != null && revision == generation && state.account.accountId == owner && state.expiresAt == expiry &&
            state.account.accountType == AccountType.CREATOR && state.account.status == AccountStatus.ACTIVE && clock.instant().isBefore(state.expiresAt)
    }
    private fun launchForOwner(block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch(ExpectedAccount(owner!!, expiry!!), block = block)

    /** Navigation calls this on entry; only the visible screen refreshes when the app resumes. */
    fun onShown(view: DiscoveryView?) {
        visible = view; clockJob?.cancel()
        if (!active() || view == null) return
        tick()
        when (view) {
            DiscoveryView.HOME -> loadOpportunities()
            DiscoveryView.SEARCH -> loadPage(ui.value.results.page?.page ?: ui.value.results.requestedPage)
            DiscoveryView.DETAIL -> if (!ui.value.detail.loading && ui.value.detail.details == null) refreshDetail()
        }
        startClock()
    }
    private fun startClock() {
        clockJob?.cancel()
        if (!active() || visible == null || !foreground) return
        clockJob = launchForOwner {
            while (isActive && active()) {
                tick()
                val deadlines = buildList {
                    ui.value.results.page?.items?.forEach { it.applicationDeadline?.let(::add) }
                    ui.value.opportunities.page?.items?.forEach { it.applicationDeadline?.let(::add) }
                    ui.value.detail.details?.summary?.applicationDeadline?.let(::add)
                }.filter { it.isAfter(clock.instant()) }
                val next = deadlines.minOrNull()
                val wait = next?.let { Duration.between(clock.instant(), it).toMillis().coerceIn(1, 30_000) } ?: 30_000
                delay(wait); tick()
            }
        }
    }
    fun onResume() {
        foreground = true
        if (!active()) return
        tick(); startClock()
        when (visible) {
            DiscoveryView.HOME -> loadOpportunities(force = true)
            DiscoveryView.SEARCH -> loadPage(ui.value.results.page?.page ?: ui.value.results.requestedPage, force = true)
            DiscoveryView.DETAIL -> refreshDetail(force = true)
            null -> Unit
        }
    }
    fun onBackground() { foreground = false; clockJob?.cancel(); clockJob = null }
    private fun tick() { mutableUi.update { it.copy(now = clock.instant()) } }
    fun consumeNavigation() { mutableUi.update { it.copy(navigateToDetail = false) } }

    fun editFilters(update: DiscoveryFilters.() -> DiscoveryFilters) {
        if (!active()) return
        searchJob?.cancel(); searchRevision++
        mutableUi.update { it.copy(filters = it.filters.update(), filterFailure = null,
            results = it.results.copy(loading = false)) }
    }
    fun applyFilters() {
        if (!active()) return
        val filters = ui.value.filters.normalized()
        filters.validation()?.let { error -> mutableUi.update { it.copy(filterFailure = error) }; return }
        searchJob?.cancel(); searchRevision++
        mutableUi.update { it.copy(filters = filters, appliedFilters = filters, filterFailure = null, results = DiscoveryPageUiState()) }
        loadPage(0)
    }
    fun clearFilters() {
        if (!active()) return
        mutableUi.update { it.copy(filters = DiscoveryFilters()) }; applyFilters()
    }
    fun retrySearch() = loadPage(ui.value.results.requestedPage, force = true)
    fun refreshSearch() = loadPage(ui.value.results.page?.page ?: 0, force = true)
    fun nextPage() {
        val state = ui.value
        if (!state.pendingFilters && !state.results.loading && state.results.hasNext) loadPage(state.results.page!!.page + 1)
    }
    fun previousPage() {
        val state = ui.value
        if (!state.pendingFilters && !state.results.loading && state.results.hasPrevious) loadPage(state.results.page!!.page - 1)
    }
    private fun loadPage(page: Int, force: Boolean = false) {
        if (!active() || page < 0 || (searchJob?.isActive == true && !force)) return
        searchJob?.cancel()
        val revision = ++searchRevision; val account = generation; val filters = ui.value.appliedFilters
        val request = PageRequest(page, 20)
        mutableUi.update { it.copy(results = it.results.copy(loading = true, failure = null, requestedPage = page)) }
        searchJob = launchForOwner {
            val response = if (filters.isEmpty) useCases.getPublished(request) else useCases.search(filters.search(), request)
            if (!active(account) || revision != searchRevision) return@launchForOwner
            acceptPage(response, request) { result -> mutableUi.update { it.copy(results = result, now = clock.instant()) } }
            startClock()
        }
    }
    fun loadOpportunities(force: Boolean = false) {
        if (!active() || (homeJob?.isActive == true && !force)) return
        homeJob?.cancel(); val revision = ++homeRevision; val account = generation; val request = PageRequest(0, 3)
        mutableUi.update { it.copy(opportunities = it.opportunities.copy(loading = true, failure = null)) }
        homeJob = launchForOwner {
            val response = useCases.getPublished(request)
            if (!active(account) || revision != homeRevision) return@launchForOwner
            acceptPage(response, request) { result -> mutableUi.update { it.copy(opportunities = result, now = clock.instant()) } }
            startClock()
        }
    }
    private fun acceptPage(response: ApiResult<Page<CampaignSummary>>, request: PageRequest, update: (DiscoveryPageUiState) -> Unit) {
        when (response) {
            is ApiResult.Failure -> update(DiscoveryPageUiState(failure = response.error, requestedPage = request.page))
            is ApiResult.Success -> {
                val page = response.value
                val invalid = page.page != request.page || page.size != request.size || page.total < 0 || page.items.size > page.size ||
                    page.items.size > page.total || page.items.map { it.id }.distinct().size != page.items.size ||
                    page.items.any { it.status != CampaignStatus.OPEN || !it.acceptsApplications || it.applicationDeadline == null }
                if (invalid) update(DiscoveryPageUiState(failure = malformed(), requestedPage = request.page))
                else update(DiscoveryPageUiState(page = page, requestedPage = request.page))
            }
        }
    }
    fun openDetails(id: UUID) {
        if (!active()) return
        detailJob?.cancel(); detailRevision++
        mutableUi.update { it.copy(detail = DiscoveryDetailUiState(id), navigateToDetail = true) }
        refreshDetail()
    }
    fun refreshDetail(force: Boolean = false) {
        val id = ui.value.detail.id ?: return
        if (!active() || (detailJob?.isActive == true && !force)) return
        detailJob?.cancel(); val revision = ++detailRevision; val account = generation
        // A failed refresh must not leave stale availability displayed as currently verified.
        mutableUi.update { it.copy(detail = DiscoveryDetailUiState(id, loading = true), now = clock.instant()) }
        detailJob = launchForOwner {
            val response = useCases.getDetails(id)
            if (!active(account) || revision != detailRevision) return@launchForOwner
            val next = when (response) {
                is ApiResult.Failure -> DiscoveryDetailUiState(id, failure = response.error)
                is ApiResult.Success -> if (response.value.summary.id != id || response.value.summary.status == CampaignStatus.DRAFT || response.value.publicationDate == null ||
                    response.value.requirements.map { it.id }.distinct().size != response.value.requirements.size ||
                    response.value.deliverables.map { it.id }.distinct().size != response.value.deliverables.size)
                    DiscoveryDetailUiState(id, failure = malformed()) else DiscoveryDetailUiState(id, details = response.value)
            }
            mutableUi.update { it.copy(detail = next, now = clock.instant()) }
            startClock()
        }
    }
    private fun malformed() = ApiFailure(FailureKind.MALFORMED_RESPONSE, message = "La respuesta de campañas no corresponde a la consulta. Actualiza para volver a verificarla.")
}
