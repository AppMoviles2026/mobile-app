package com.example.collabpro.features.performance.presentation.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.collabpro.core.application.security.ExpectedAccount
import com.example.collabpro.core.domain.*
import com.example.collabpro.core.designsystem.displayTime
import com.example.collabpro.features.campaign.domain.repositories.ApplicantEvaluationRepository
import com.example.collabpro.features.collaboration.domain.model.CollaborationStatus
import com.example.collabpro.features.collaboration.domain.model.DeliveryRecord
import com.example.collabpro.features.collaboration.domain.repositories.CollaborationRepository
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.application.auth.SessionState
import com.example.collabpro.features.identity.domain.model.Account
import com.example.collabpro.features.identity.domain.model.AccountType
import com.example.collabpro.features.identity.domain.model.SocialAccountStatus
import com.example.collabpro.features.identity.domain.repositories.IdentityRepository
import com.example.collabpro.features.performance.application.PerformanceInputs
import com.example.collabpro.features.performance.domain.model.*
import com.example.collabpro.features.performance.domain.repositories.PerformanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

internal enum class PerformanceTab { METRICS, SNAPSHOTS, EVIDENCE, EVIDENCE_DETAIL, ATTRIBUTION, INTERACTIONS }
internal data class SocialOption(val id: UUID, val username: String)
internal data class SubmissionOption(val id: UUID, val label: String, val postId: String?)
internal data class PerformanceScope(val account: Account, val expires: Instant, val collaboration: UUID) {
    val brand get() = account.accountType == AccountType.BRAND
}
internal data class PerformanceState(
    val scope: PerformanceScope? = null,
    val tab: PerformanceTab = PerformanceTab.METRICS,
    val title: String? = null,
    val writable: Boolean = false,
    val metrics: MetricsReport? = null,
    val snapshots: Page<Snapshot>? = null,
    val evidence: Page<Evidence>? = null,
    val evidenceFilter: String? = null,
    val evidenceId: UUID? = null,
    val evidenceReturn: PerformanceTab = PerformanceTab.EVIDENCE,
    val selectedEvidence: Evidence? = null,
    val attribution: AttributionReport? = null,
    val period: Period? = null,
    val destinations: List<Destination>? = null,
    val mechanism: Mechanism? = null,
    val interactions: Page<Interaction>? = null,
    val supportingEvidence: List<Evidence>? = null,
    val lastInteraction: UUID? = null,
    val socials: List<SocialOption>? = null,
    val submissions: List<SubmissionOption>? = null,
    val uploads: List<Asset> = emptyList(),
    val export: Asset? = null,
    val busy: Boolean = false,
    val uncertain: Boolean = false,
    val failure: String? = null,
    val notice: String? = null
)

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val repository: PerformanceRepository,
    private val collaborations: CollaborationRepository,
    private val identity: IdentityRepository,
    private val applicants: ApplicantEvaluationRepository,
    private val session: AuthenticationSession,
    private val clock: Clock
) : ViewModel() {
    private val state = MutableStateFlow(PerformanceState())
    internal val ui = state.asStateFlow()
    private var work: Job? = null
    private var pending: (suspend (PerformanceScope) -> Unit)? = null
    private var requestedCollaboration: UUID? = null

    init {
        viewModelScope.launch {
            session.state.collect {
                val scope = state.value.scope
                if (scope != null && !isCurrent(scope)) {
                    requestedCollaboration = null
                    work?.cancel()
                    pending = null
                    state.value = PerformanceState()
                }
            }
        }
    }

    internal fun open(id: UUID) {
        val actor = session.state.value as? SessionState.Authenticated ?: return
        val scope = PerformanceScope(actor.account, actor.expiresAt, id)
        if (!isCurrent(scope)) return
        if (state.value.scope != scope) {
            if (state.value.busy || state.value.uncertain) {
                requestedCollaboration = id
                return
            }
            requestedCollaboration = null
            pending = null
            state.value = PerformanceState(scope = scope)
        }
        refresh()
    }

    internal fun select(tab: PerformanceTab) {
        if (state.value.busy || state.value.uncertain) return
        change { copy(tab = tab, failure = null, notice = null) }
        refresh()
    }

    internal fun back(): Boolean = when (state.value.tab) {
        PerformanceTab.EVIDENCE_DETAIL -> { select(state.value.evidenceReturn); true }
        PerformanceTab.INTERACTIONS -> { select(PerformanceTab.ATTRIBUTION); true }
        PerformanceTab.SNAPSHOTS -> { select(PerformanceTab.METRICS); true }
        else -> false
    }

    internal fun refresh() = launchWork { scope -> load(scope) }

    private suspend fun load(scope: PerformanceScope, page: Int? = null) {
        val record = receive(scope, collaborations.collaboration(scope.collaboration)) ?: run {
            if (isCurrent(scope)) change { copy(writable = false) }
            return
        }
        change { copy(title = record.snapshot.title, writable = record.status != CollaborationStatus.CANCELLED) }
        val current = state.value
        when (current.tab) {
            PerformanceTab.METRICS -> receive(scope, repository.metrics(scope.collaboration, PageRequest(page ?: current.metrics?.publications?.page ?: 0)))
                ?.let { change { copy(metrics = it) } }
            PerformanceTab.SNAPSHOTS -> receive(scope, repository.snapshots(scope.collaboration, PageRequest(page ?: current.snapshots?.page ?: 0)))
                ?.let { change { copy(snapshots = it) } }
            PerformanceTab.EVIDENCE -> receive(scope, repository.evidence(scope.collaboration, PageRequest(page ?: current.evidence?.page ?: 0), current.evidenceFilter))
                ?.let { change { copy(evidence = it) } }
            PerformanceTab.EVIDENCE_DETAIL -> current.evidenceId?.let { id -> loadEvidence(scope, id) }
            PerformanceTab.ATTRIBUTION -> {
                receive(scope, repository.attribution(scope.collaboration, PageRequest(page ?: current.attribution?.mechanisms?.page ?: 0), current.period))
                    ?.let { change { copy(attribution = it) } }
                if (scope.brand && current.writable) receive(scope, repository.destinations())?.let { change { copy(destinations = it) } }
            }
            PerformanceTab.INTERACTIONS -> current.mechanism?.let { mechanism ->
                receive(scope, repository.interactions(mechanism.id, PageRequest(page ?: current.interactions?.page ?: 0), current.period))
                    ?.let { change { copy(interactions = it) } }
            }
        }
    }

    internal fun page(number: Int) = launchWork { if (number >= 0) load(it, number) }

    internal fun filterEvidence(status: String?) {
        if (state.value.busy || state.value.uncertain) return
        change { copy(evidenceFilter = status, evidence = null) }
        refresh()
    }

    internal fun filterAttribution(period: Period?) {
        if (state.value.busy || state.value.uncertain) return
        change { copy(period = period, attribution = null, interactions = null) }
        refresh()
    }

    internal fun inspectEvidence(id: UUID) {
        if (state.value.busy || state.value.uncertain) return
        change { copy(tab = PerformanceTab.EVIDENCE_DETAIL, evidenceId = id, evidenceReturn = tab, selectedEvidence = null) }
        refresh()
    }

    private suspend fun loadEvidence(scope: PerformanceScope, id: UUID) {
        val record = receive(scope, repository.evidenceDetail(id))
        if (record?.collaborationId == scope.collaboration) change { copy(selectedEvidence = record) }
        else if (isCurrent(scope)) change { copy(selectedEvidence = null) }
    }

    internal fun inspectInteractions(item: Mechanism) {
        if (state.value.busy || state.value.uncertain || item.collaborationId != state.value.scope?.collaboration) return
        change { copy(tab = PerformanceTab.INTERACTIONS, mechanism = item, interactions = null, supportingEvidence = null, lastInteraction = null) }
        refresh()
    }

    internal fun loadSources(automatic: Boolean) = launchWork { scope ->
        val deliveries = collectPages(scope) { page -> collaborations.deliveries(scope.collaboration, null, page) } ?: return@launchWork
        val submissions = deliveries.filter { it.status != "REJECTED" }.map { it.submissionOption() }
        change { copy(submissions = submissions) }
        if (!automatic) return@launchWork
        val socials = if (scope.brand) {
            val collaboration = receive(scope, collaborations.collaboration(scope.collaboration)) ?: return@launchWork
            val snapshot = collaboration.snapshot
            val applicant = receive(scope, applicants.detail(snapshot.campaignId, snapshot.applicationId)) ?: return@launchWork
            applicant.creator?.takeIf { it.id == snapshot.creatorId && it.active }?.socialAccounts.orEmpty()
                .filter { it.platform.equals("tiktok", ignoreCase = true) }.map { SocialOption(it.id, it.username) }
        } else {
            receive(scope, identity.socialAccounts())?.filter { it.status == SocialAccountStatus.ACTIVE && it.platform.wireValue == "tiktok" }
                ?.map { SocialOption(it.id, it.username) } ?: return@launchWork
        }
        change { copy(socials = socials) }
    }

    private fun DeliveryRecord.submissionOption() = SubmissionOption(currentSubmission.id,
        "Pieza $pieceNumber · versión ${currentSubmission.revision} · ${currentSubmission.submittedAt.displayTime()}",
        PerformanceInputs.videoId(currentSubmission.content.content.url))

    internal fun refreshMetrics(social: SocialOption, submission: SubmissionOption) {
        val current = state.value
        val post = submission.postId ?: return
        if (social !in current.socials.orEmpty() || submission !in current.submissions.orEmpty()) return
        val command = RefreshMetrics(UUID.randomUUID(), social.id, submission.id, post)
        write { scope ->
            send(scope, repository.refresh(scope.collaboration, command)) {
                load(scope, 0)
                change { copy(notice = "Consulta al proveedor: ${it.status.performanceLabel()}") }
            }
        }
    }

    internal fun upload(file: Document, collaboration: UUID) {
        if (state.value.scope?.collaboration != collaboration || state.value.uploads.size >= 10) return
        val key = UUID.randomUUID()
        write(brand = false) { scope ->
            send(scope, repository.upload(scope.collaboration, key, file)) { asset ->
                change { copy(uploads = (uploads + asset).distinctBy(Asset::id), notice = "Archivo guardado para esta evidencia.") }
            }
        }
    }

    internal fun removeUpload(asset: Asset) {
        if (!state.value.busy && !state.value.uncertain) change { copy(uploads = uploads - asset) }
    }

    internal fun register(command: RegisterEvidence) {
        val current = state.value
        if (command.references.mapNotNull { it.assetId }.any { id -> current.uploads.none { it.id == id } }) return
        write(brand = false) { scope ->
            send(scope, repository.register(scope.collaboration, command)) { result ->
                change { copy(uploads = emptyList(), tab = PerformanceTab.EVIDENCE_DETAIL,
                    evidenceId = result.id, evidenceReturn = PerformanceTab.EVIDENCE, selectedEvidence = result, notice = "Evidencia registrada.") }
            }
        }
    }

    internal fun review(evidence: Evidence, decision: EvidenceDecision, notes: String?) {
        if (state.value.selectedEvidence != evidence || evidence.status != "UNVERIFIED") return
        val command = ReviewEvidence(UUID.randomUUID(), evidence.version, decision, notes)
        write(brand = true) { scope ->
            if (!send(scope, repository.review(evidence.id, command)) {
                    change { copy(selectedEvidence = it, notice = "Revisión registrada.") }
                } && !state.value.uncertain) loadEvidence(scope, evidence.id)
        }
    }

    internal fun chooseAsset(id: UUID) = launchWork { scope ->
        receive(scope, repository.asset(id))?.let { change { copy(export = it) } }
    }

    internal fun clearExport() = change { copy(export = null) }

    internal fun download(asset: Asset, scope: PerformanceScope, save: suspend (ByteArray) -> Unit) = launchWork { active ->
        if (scope != active) return@launchWork
        receive(active, repository.download(asset))?.let {
            try {
                save(it)
                if (isCurrent(active)) change { copy(notice = "Archivo guardado.") }
            } catch (_: java.io.IOException) {
                if (isCurrent(active)) failure("No se pudo guardar el archivo.")
            } catch (_: SecurityException) {
                if (isCurrent(active)) failure("Android no permite escribir en ese destino.")
            }
        }
    }

    internal fun create(kind: MechanismKind, label: String, destination: Destination?) {
        if (kind == MechanismKind.LINK && destination !in state.value.destinations.orEmpty()) return
        val command = CreateMechanism(UUID.randomUUID(), kind, label, destination?.id)
        write(brand = true) { scope ->
            send(scope, repository.create(scope.collaboration, command)) {
                change { copy(period = null) }
                load(scope, 0)
                change { copy(notice = "Mecanismo creado.") }
            }
        }
    }

    internal fun changeStatus(item: Mechanism) {
        if (item !in state.value.attribution?.mechanisms?.items.orEmpty().map { it.mechanism }) return
        val status = if (item.status == MechanismStatus.ACTIVE) MechanismStatus.INACTIVE else MechanismStatus.ACTIVE
        val command = ChangeMechanism(UUID.randomUUID(), item.version, status)
        write(brand = true) { scope ->
            send(scope, repository.change(item.id, command)) { change { copy(notice = "Estado actualizado.") } }
            if (!state.value.uncertain) load(scope)
        }
    }

    internal fun loadSupportingEvidence() = launchWork { scope ->
        if (!scope.brand) return@launchWork
        val mechanism = state.value.mechanism ?: return@launchWork
        val evidence = collectPages(scope) { page -> repository.evidence(scope.collaboration, page, null) } ?: return@launchWork
        change { copy(supportingEvidence = evidence.filter { it.status != "REJECTED" && it.demonstration == mechanism.demonstration }) }
    }

    internal fun record(mechanism: Mechanism, type: InteractionType, occurredAt: Instant, evidence: Evidence) {
        val current = state.value
        if (current.mechanism != mechanism || evidence !in current.supportingEvidence.orEmpty() ||
            (type == InteractionType.CODE_REDEMPTION && mechanism.kind != MechanismKind.CODE) ||
            occurredAt < mechanism.createdAt || occurredAt > clock.instant()) return
        val command = RecordInteraction(UUID.randomUUID(), UUID.randomUUID(), type, occurredAt, evidence.id)
        write(brand = true) { scope ->
            send(scope, repository.record(mechanism.id, command)) {
                load(scope, 0)
                change { copy(lastInteraction = it.id, notice = "Interacción registrada.") }
            }
        }
    }

    internal fun retry() {
        val command = pending ?: return
        launchWork(command)
    }

    internal fun now(): Instant = clock.instant()

    internal fun failure(message: String) = change { copy(failure = message) }

    private fun write(brand: Boolean? = null, command: suspend (PerformanceScope) -> Unit) {
        val current = state.value
        val scope = current.scope ?: return
        if (!isCurrent(scope) || current.busy || pending != null || !current.writable ||
            (brand != null && scope.brand != brand)) return
        pending = command
        launchWork(command)
    }

    private fun launchWork(action: suspend (PerformanceScope) -> Unit) {
        val scope = state.value.scope ?: return
        if (!isCurrent(scope) || state.value.busy) return
        change { copy(busy = true, failure = null, notice = null) }
        work = viewModelScope.launch(ExpectedAccount(scope.account.accountId, scope.expires)) {
            try { action(scope) }
            finally {
                if (isCurrent(scope) && state.value.scope == scope) {
                    change { copy(busy = false) }
                    val next = requestedCollaboration
                    if (next != null && pending == null) {
                        requestedCollaboration = null
                        open(next)
                    }
                }
            }
        }
    }

    private fun isCurrent(scope: PerformanceScope): Boolean {
        val actor = session.state.value as? SessionState.Authenticated ?: return false
        return actor.account.accountId == scope.account.accountId && actor.expiresAt == scope.expires && clock.instant() < scope.expires
    }

    private fun change(update: PerformanceState.() -> PerformanceState) { state.value = state.value.update() }

    private fun <T> receive(scope: PerformanceScope, result: ApiResult<T>): T? {
        if (!isCurrent(scope) || state.value.scope != scope) return null
        return when (result) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> { failure(result.error.message); null }
        }
    }

    private suspend fun <T> send(scope: PerformanceScope, result: ApiResult<T>, accept: suspend (T) -> Unit): Boolean {
        if (!isCurrent(scope) || state.value.scope != scope) return false
        val error = (result as? ApiResult.Failure)?.error
        val uncertain = error?.httpStatus?.let { it >= 500 || it == 408 || it == 429 }
            ?: (error?.kind in listOf(FailureKind.NETWORK, FailureKind.TIMEOUT, FailureKind.MALFORMED_RESPONSE, FailureKind.SERVER))
        if (!uncertain) pending = null
        change { copy(uncertain = uncertain) }
        val value = receive(scope, result) ?: return false
        accept(value)
        return true
    }

    private suspend fun <T> collectPages(scope: PerformanceScope, query: suspend (PageRequest) -> ApiResult<Page<T>>): List<T>? {
        val rows = mutableListOf<T>()
        var number = 0
        while (isCurrent(scope)) {
            val page = receive(scope, query(PageRequest(number++, 100))) ?: return null
            rows += page.items
            if (page.items.isEmpty() || number.toLong() * page.size >= page.total) return rows
        }
        return null
    }
}
