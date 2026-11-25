package com.example.collabpro.features.collaboration.presentation.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.collabpro.core.application.security.ExpectedAccount
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.collaboration.domain.model.*
import com.example.collabpro.features.collaboration.domain.repositories.CollaborationRepository
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.application.auth.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CollaborationWorkspaceViewModel @Inject constructor(
    private val repository: CollaborationRepository,
    private val authentication: AuthenticationSession
) : ViewModel() {
    private val mutableUi = MutableStateFlow(CollaborationUiState())
    internal val ui = mutableUi.asStateFlow()
    private val backStack = mutableListOf<Destination>()
    private var job: Job? = null
    private var retryWrite: (suspend () -> Unit)? = null

    init {
        viewModelScope.launch {
            authentication.state.collect { if (!owns(ui.value)) synchronize() }
        }
    }

    private fun session() = authentication.state.value as? SessionState.Authenticated

    private fun owns(state: CollaborationUiState): Boolean {
        val session = session() ?: return false
        return state.owner == session.account.accountId && state.expiry == session.expiresAt
    }

    private fun synchronize(): Boolean {
        val session = session()
        if (!owns(ui.value)) {
            job?.cancel()
            backStack.clear()
            retryWrite = null
            mutableUi.value = CollaborationUiState(owner = session?.account?.accountId, expiry = session?.expiresAt)
        }
        return session != null
    }

    internal fun enter() {
        if (synchronize() && ui.value.content == null && !ui.value.busy && !ui.value.uncertain) refresh()
    }

    internal fun propose(applicationId: UUID): Boolean {
        if (!synchronize() || ui.value.busy || ui.value.uncertain) return false
        backStack.clear()
        backStack.add(Destination())
        show(Destination(CollaborationPage.PROPOSAL, resourceId = applicationId))
        return true
    }

    internal fun open(destination: Destination) {
        if (!synchronize() || blocked()) return
        backStack.add(ui.value.destination)
        show(destination)
    }

    internal fun page(number: Int, filter: String? = ui.value.destination.filter) {
        if (number < 0 || blocked()) return
        show(ui.value.destination.copy(number = number, filter = filter))
    }

    internal fun back(): Boolean {
        if (blocked()) return true
        if (backStack.isEmpty()) return false
        show(backStack.removeAt(backStack.lastIndex))
        return true
    }

    private fun blocked() = ui.value.busy || ui.value.uncertain

    private fun show(destination: Destination, notice: String? = null) {
        mutableUi.value = ui.value.copy(
            destination = destination, content = null, failure = null, countBack = backStack.size,
            notice = notice, stale = false, uploads = emptyList(), export = null, extensionPieces = emptyList()
        )
        refresh()
    }

    internal fun refresh() {
        if (ui.value.busy) return
        launch {
            val state = ui.value
            val destination = state.destination
            val page = PageRequest(destination.number)
            val id = destination.resourceId
            val collaboration = destination.collaborationId
            val status = destination.filter?.let { value -> CollaborationStatus.entries.find { it.name == value } }
            val result = when (destination.page) {
                CollaborationPage.HOME -> repository.collaborations(status, page).map { CollaborationContent.Collaborations(it) }
                CollaborationPage.AGREEMENTS -> repository.agreements(destination.filter, page).map { CollaborationContent.Agreements(it) }
                CollaborationPage.PROPOSAL -> repository.proposeAgreement(ProposeAgreement(id!!)).map { CollaborationContent.Agreement(it) }
                CollaborationPage.AGREEMENT -> repository.agreement(id!!).map { CollaborationContent.Agreement(it) }
                CollaborationPage.DETAIL, CollaborationPage.SUBMIT, CollaborationPage.OPEN_INCIDENT ->
                    repository.collaboration(collaboration!!).map { CollaborationContent.Detail(it) }
                CollaborationPage.DELIVERIES -> repository.deliveries(collaboration!!, destination.filter, page).map { CollaborationContent.Deliveries(it) }
                CollaborationPage.DELIVERY, CollaborationPage.CORRECT -> repository.delivery(id!!).map { CollaborationContent.Delivery(it) }
                CollaborationPage.REVISIONS -> repository.revisions(id!!, page).map { CollaborationContent.Revisions(it) }
                CollaborationPage.INCIDENTS -> repository.incidents(collaboration!!, destination.filter, page).map { CollaborationContent.Incidents(it) }
                CollaborationPage.INCIDENT, CollaborationPage.PROPOSE_RESOLUTION -> repository.incident(id!!).map { CollaborationContent.Incident(it) }
                CollaborationPage.RESOLUTIONS -> repository.resolutions(id!!, page).map { CollaborationContent.Resolutions(it) }
                CollaborationPage.HISTORY -> repository.history(status, page).map { CollaborationContent.Collaborations(it) }
                CollaborationPage.HISTORY_DETAIL -> repository.historyDetail(collaboration!!).map { CollaborationContent.History(it) }
                CollaborationPage.TIMELINE -> repository.timeline(collaboration!!, page).map { CollaborationContent.Timeline(it) }
            }
            if (!owns(state)) return@launch
            when (result) {
                is ApiResult.Success -> mutableUi.value = ui.value.copy(content = result.value, stale = false)
                is ApiResult.Failure -> fail(result.error)
            }
        }
    }

    internal fun decideAgreement(record: AgreementRecord, decision: AgreementDecision) {
        write({ repository.decideAgreement(record.id, DecideAgreement(record.snapshot.termsVersion, decision)) }) { next ->
            replaceContent(CollaborationContent.Agreement(next), "Decisión registrada.")
        }
    }

    internal fun recover(record: AgreementRecord) {
        write({ repository.recoverCollaboration(RecoverCollaboration(record.id)) }) {
            backStack.add(ui.value.destination)
            goWithRecord(Destination(CollaborationPage.DETAIL, it.id), CollaborationContent.Detail(it), "Colaboración recuperada.")
        }
    }

    internal fun submit(collaborationId: UUID, obligation: UUID, content: DeliveryBody) {
        val command = SubmitDelivery(UUID.randomUUID(), obligation, content)
        write({ repository.submit(collaborationId, command) }) { deliverySaved(it, "Entrega registrada para revisión.") }
    }

    internal fun correct(previous: DeliveryRecord, body: DeliveryBody) {
        val command = ReviseDelivery(UUID.randomUUID(), previous.version, body)
        write({ repository.revise(previous.id, command) }) { deliverySaved(it, "Nueva versión enviada para revisión.") }
    }

    internal fun review(previous: DeliveryRecord, approve: Boolean, observations: String?, deadline: Instant?) {
        val command = ReviewDelivery(UUID.randomUUID(), previous.currentSubmission.id, previous.version, observations, deadline)
        write({ if (approve) repository.approve(previous.id, command) else repository.reject(previous.id, command) }) {
            replaceContent(CollaborationContent.Delivery(it), "Revisión registrada.")
        }
    }

    private fun deliverySaved(record: DeliveryRecord, notice: String) {
        if (ui.value.destination.page == CollaborationPage.CORRECT && backStack.lastOrNull()?.resourceId == record.id)
            backStack.removeAt(backStack.lastIndex)
        goWithRecord(Destination(CollaborationPage.DELIVERY, record.collaborationId, record.id),
            CollaborationContent.Delivery(record), notice)
    }

    internal fun report(id: UUID, category: IncidentCategory, description: String,
        delivery: UUID?, evidence: List<ContentReference>) {
        val command = OpenIncident(UUID.randomUUID(), category, description, delivery, evidence)
        write({ repository.openIncident(id, command) }) { incidentSaved(it, "Incidencia registrada.") }
    }

    internal fun proposeResolution(previous: IncidentRecord, terms: ResolutionTerms) {
        val command = ProposeResolution(UUID.randomUUID(), previous.version, terms)
        write({ repository.proposeResolution(previous.id, command) }) { incidentSaved(it, "Propuesta enviada a la contraparte.") }
    }

    internal fun decideResolution(previous: IncidentRecord, proposal: ResolutionRecord, decision: ResolutionDecision, reason: String?) {
        val command = DecideResolution(UUID.randomUUID(), previous.version, decision, reason)
        write({ repository.decideResolution(previous.id, proposal.id, command) }) {
            replaceContent(CollaborationContent.Incident(it), "Decisión de resolución registrada.")
        }
    }

    private fun incidentSaved(record: IncidentRecord, notice: String) {
        if (ui.value.destination.page == CollaborationPage.PROPOSE_RESOLUTION && backStack.lastOrNull()?.resourceId == record.id)
            backStack.removeAt(backStack.lastIndex)
        goWithRecord(Destination(CollaborationPage.INCIDENT, record.collaborationId, record.id),
            CollaborationContent.Incident(record), notice)
    }

    internal fun loadExtensionPieces(id: UUID) {
        if (blocked()) return
        launch {
            val state = ui.value
            val records = mutableListOf<DeliveryRecord>()
            var number = 0
            while (owns(state)) {
                when (val result = repository.deliveries(id, "REJECTED", PageRequest(number, 100))) {
                    is ApiResult.Failure -> { fail(result.error); return@launch }
                    is ApiResult.Success -> {
                        records += result.value.items
                        number++
                        if (number.toLong() * result.value.size >= result.value.total || result.value.items.isEmpty()) break
                    }
                }
            }
            if (owns(state)) mutableUi.value = ui.value.copy(extensionPieces = records)
        }
    }

    internal fun upload(id: UUID, document: UploadDocument, incident: Boolean) {
        val key = UUID.randomUUID()
        write({ repository.upload(id, key, document, incident) }) { asset ->
            mutableUi.value = ui.value.copy(uploads = (ui.value.uploads + asset).distinctBy { it.id },
                notice = "Archivo adjunto a la preparación.")
        }
    }

    internal fun removeUpload(id: UUID) {
        if (blocked()) return
        mutableUi.value = ui.value.copy(uploads = ui.value.uploads.filterNot { it.id == id })
    }

    internal fun prepareExport(id: UUID, collaboration: UUID) {
        if (blocked()) return
        launch {
            val state = ui.value
            when (val result = repository.asset(id)) {
                is ApiResult.Success -> if (owns(state)) {
                    if (result.value.collaborationId == collaboration) mutableUi.value = ui.value.copy(export = result.value)
                    else fail(ApiFailure(FailureKind.MALFORMED_RESPONSE, message = "El archivo no corresponde a esta colaboración."))
                }
                is ApiResult.Failure -> if (owns(state)) fail(result.error)
            }
        }
    }

    internal fun exportHandled() { mutableUi.value = ui.value.copy(export = null) }

    internal fun export(asset: AssetRecord, writeFile: suspend (ByteArray) -> Unit) {
        if (blocked()) return
        launch {
            val state = ui.value
            when (val result = repository.download(asset.id)) {
                is ApiResult.Success -> if (owns(state)) {
                    try {
                        writeFile(result.value)
                        if (owns(state)) mutableUi.value = ui.value.copy(notice = "Archivo guardado.")
                    } catch (error: java.io.IOException) {
                        if (owns(state)) fail(ApiFailure(FailureKind.STORAGE, message = "No se pudo escribir el archivo elegido."))
                    } catch (error: SecurityException) {
                        if (owns(state)) fail(ApiFailure(FailureKind.STORAGE, message = "El destino ya no permite guardar este archivo."))
                    }
                }
                is ApiResult.Failure -> if (owns(state)) fail(result.error)
            }
        }
    }

    internal fun reportLocalError(message: String) = fail(ApiFailure(FailureKind.VALIDATION, message = message))

    internal fun retry() {
        val write = retryWrite ?: return
        if (!ui.value.busy && owns(ui.value)) launch(write)
    }

    private fun <T> write(call: suspend () -> ApiResult<T>, apply: (T) -> Unit) {
        if (blocked() || ui.value.stale || !synchronize()) return
        val state = ui.value
        val request: suspend () -> Unit = request@ {
            val result = call()
            if (!owns(state)) return@request
            when (result) {
                is ApiResult.Success -> {
                    retryWrite = null
                    mutableUi.value = ui.value.copy(uncertain = false)
                    apply(result.value)
                }
                is ApiResult.Failure -> {
                    val status = result.error.httpStatus
                    val unknown = if (status != null) status >= 500 || status in listOf(408, 429)
                        else result.error.kind in setOf(FailureKind.NETWORK, FailureKind.TIMEOUT, FailureKind.MALFORMED_RESPONSE)
                    if (!unknown) retryWrite = null
                    mutableUi.value = ui.value.copy(uncertain = unknown, failure = result.error)
                    if (result.error.kind == FailureKind.CONFLICT) {
                        // Re-read the displayed conditional resource before another decision.
                        mutableUi.value = ui.value.copy(stale = true)
                    }
                }
            }
        }
        retryWrite = request
        launch(request)
    }

    private fun launch(block: suspend () -> Unit) {
        if (!synchronize() || ui.value.busy) return
        val owner = ui.value
        mutableUi.value = owner.copy(busy = true, failure = null)
        job = viewModelScope.launch(ExpectedAccount(owner.owner!!, owner.expiry!!)) {
            try { block() } finally {
                if (owns(owner)) mutableUi.value = ui.value.copy(busy = false)
            }
        }
    }

    private fun fail(error: ApiFailure) { mutableUi.value = ui.value.copy(failure = error) }
    private fun replaceContent(content: CollaborationContent, notice: String) {
        mutableUi.value = ui.value.copy(content = content, notice = notice)
    }
    private fun goWithRecord(destination: Destination, content: CollaborationContent, notice: String) {
        mutableUi.value = ui.value.copy(destination = destination, content = content, notice = notice,
            uploads = emptyList(), countBack = backStack.size, extensionPieces = emptyList())
    }
    private fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(value))
        is ApiResult.Failure -> this
    }
}
