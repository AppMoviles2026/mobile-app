package com.example.collabpro.features.campaign.presentation.applications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.collabpro.core.application.security.ExpectedAccount
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.applications.*
import com.example.collabpro.features.campaign.application.usecases.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.identity.application.auth.*
import com.example.collabpro.features.identity.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class OwnApplicationsViewModel @Inject constructor(private val applications: ApplicationUseCases,
    private val campaigns: CampaignUseCases, private val findOwn: FindOwnApplication,
    private val authentication: AuthenticationSession, private val clock: Clock) : ViewModel() {
    private val mutableUi = MutableStateFlow(OwnApplicationsUiState())
    val ui = mutableUi.asStateFlow()
    private var owner: Account? = null
    private var expiry: Instant? = null
    private var generation = 0L
    private var listRevision = 0L
    private var detailRevision = 0L
    private var formRevision = 0L
    private var listJob: Job? = null
    private var detailJob: Job? = null
    private var operationJob: Job? = null
    private var visible: OwnApplicationView? = null
    // Memory-only drafts survive navigation/rotation, never another account or a new verified session.
    private val forms = mutableMapOf<UUID, OwnApplicationFormUiState>()
    init { viewModelScope.launch { authentication.state.collect { state ->
        val current = (state as? SessionState.Authenticated)?.takeIf { it.account.accountType == AccountType.CREATOR && it.account.status == AccountStatus.ACTIVE }
        if (owner?.accountId != current?.account?.accountId || expiry != current?.expiresAt) {
            generation++; listRevision++; detailRevision++; formRevision++
            listJob?.cancel(); detailJob?.cancel(); operationJob?.cancel(); forms.clear(); visible = null
            owner = current?.account; expiry = current?.expiresAt
            mutableUi.value = OwnApplicationsUiState(ownerId = owner?.accountId, expiresAt = expiry, now = clock.instant())
        } else owner = current?.account
    } } }
    private fun active(revision: Long = generation): Boolean {
        val session = authentication.state.value as? SessionState.Authenticated ?: return false
        return owner != null && generation == revision && owner?.accountId == session.account.accountId && expiry == session.expiresAt &&
            session.account.accountType == AccountType.CREATOR && session.account.status == AccountStatus.ACTIVE && clock.instant().isBefore(session.expiresAt)
    }
    private fun launchForOwner(block: suspend CoroutineScope.() -> Unit) = viewModelScope.launch(ExpectedAccount(owner!!.accountId, expiry!!), block = block)
    private fun setForm(form: OwnApplicationFormUiState) {
        form.campaignId?.let { forms[it] = form }
        mutableUi.update { it.copy(form = form, now = clock.instant()) }
    }
    private fun formError(error: ApiFailure, notice: String? = null) = setForm(ui.value.form.copy(loading = false, busy = false, failure = error, notice = notice))
    fun consumeNavigation() { mutableUi.update { it.copy(navigate = null) } }
    fun onShown(view: OwnApplicationView?) {
        visible = view
        if (!active()) return
        mutableUi.update { it.copy(now = clock.instant()) }
        if (view == OwnApplicationView.LIST) loadList(ui.value.list.page?.page ?: ui.value.list.requestedPage)
    }
    fun onResume() {
        if (!active()) return
        mutableUi.update { it.copy(now = clock.instant()) }
        when (visible) {
            OwnApplicationView.LIST -> loadList(ui.value.list.page?.page ?: ui.value.list.requestedPage, force = true)
            OwnApplicationView.DETAIL -> refreshDetail()
            OwnApplicationView.FORM -> verifyForm()
            null -> Unit
        }
    }
    fun loadList(page: Int = 0, force: Boolean = false) {
        if (!active() || page < 0 || (listJob?.isActive == true && !force)) return
        listJob?.cancel(); val revision = ++listRevision; val account = generation; val creator = owner!!.profileId
        mutableUi.update { it.copy(list = it.list.copy(loading = true, failure = null, requestedPage = page)) }
        listJob = launchForOwner {
            val result = applications.getOwn(PageRequest(page, 20))
            if (!active(account) || revision != listRevision) return@launchForOwner
            val next = when (result) {
                is ApiResult.Failure -> OwnApplicationsListUiState(requestedPage = page, failure = result.error)
                is ApiResult.Success -> {
                    val value = result.value
                    if (value.page != page || value.size != 20 || value.total < 0 || value.items.size > 20 || value.items.size > value.total ||
                        value.items.any { !it.belongsTo(creator) } || value.items.map { it.id }.distinct().size != value.items.size)
                        OwnApplicationsListUiState(requestedPage = page, failure = malformed())
                    else OwnApplicationsListUiState(value, page)
                }
            }
            mutableUi.update { it.copy(list = next, now = clock.instant()) }
        }
    }
    fun nextPage() { val state = ui.value.list; if (!state.loading && state.hasNext) loadList(state.page!!.page + 1) }
    fun previousPage() { val state = ui.value.list; if (!state.loading && state.hasPrevious) loadList(state.page!!.page - 1) }
    fun retryList() = loadList(ui.value.list.requestedPage, force = true)

    fun openDetails(id: UUID) {
        if (!active() || ui.value.form.busy) return
        mutableUi.update { it.copy(detail = OwnApplicationDetailUiState(id), navigate = OwnApplicationView.DETAIL) }
        refreshDetail()
    }
    fun refreshDetail() {
        val id = ui.value.detail.id ?: return
        if (!active() || ui.value.form.busy) return
        detailJob?.cancel(); val revision = ++detailRevision; val account = generation; val creator = owner!!.profileId
        mutableUi.update { it.copy(detail = OwnApplicationDetailUiState(id, loading = true)) }
        detailJob = launchForOwner {
            val response = applications.getDetails(id)
            if (!active(account) || revision != detailRevision) return@launchForOwner
            val state = when (response) {
                is ApiResult.Failure -> OwnApplicationDetailUiState(id, failure = response.error)
                is ApiResult.Success -> if (!response.value.belongsTo(creator, id = id)) OwnApplicationDetailUiState(id, failure = malformed())
                    else { recordRemote(response.value); OwnApplicationDetailUiState(id, response.value,
                        pendingOperation = forms[response.value.campaignId]?.pendingWrite != null) }
            }
            mutableUi.update { it.copy(detail = state, now = clock.instant()) }
        }
    }
    private fun recordRemote(remote: Application) {
        val previous = forms[remote.campaignId] ?: return
        val base = previous.application ?: return
        if (base.id != remote.id || previous.pendingWrite != null) return
        val next = previous.copy(latest = remote.takeIf { it != base })
        forms[remote.campaignId] = next
        if (ui.value.form.campaignId == remote.campaignId) setForm(next)
    }
    fun beginProposal(campaignId: UUID) {
        if (!active() || operationJob?.isActive == true) return
        val previous = forms[campaignId] ?: OwnApplicationFormUiState(campaignId = campaignId)
        setForm(previous.copy(loading = true, busy = false, checkedAbsence = false, failure = null, notice = null))
        mutableUi.update { it.copy(navigate = OwnApplicationView.FORM) }
        loadForm()
    }
    fun editSelected() {
        val remote = ui.value.detail.application ?: return
        if (!active() || operationJob?.isActive == true || remote.status != ApplicationStatus.PENDING || ui.value.detail.loading || ui.value.detail.pendingOperation) return
        val previous = forms[remote.campaignId] ?: OwnApplicationFormUiState(campaignId = remote.campaignId)
        setForm(bindExisting(previous, remote).copy(loading = true))
        mutableUi.update { it.copy(navigate = OwnApplicationView.FORM) }
        loadForm()
    }
    private fun loadForm() {
        val id = ui.value.form.campaignId ?: return
        val revision = ++formRevision; val account = generation; val creator = owner!!.profileId
        operationJob = launchForOwner {
            val campaign = campaigns.getDetails(id)
            if (!active(account) || revision != formRevision) return@launchForOwner
            when (campaign) {
                is ApiResult.Failure -> {
                    formError(campaign.error, "Tu propuesta se conserva. No se pudo verificar las condiciones.")
                    return@launchForOwner
                }
                is ApiResult.Success -> {
                    if (campaign.value.summary.id != id || campaign.value.summary.status == CampaignStatus.DRAFT || campaign.value.publicationDate == null ||
                        campaign.value.requirements.map { it.id }.distinct().size != campaign.value.requirements.size) { formError(malformed()); return@launchForOwner }
                    setForm(ui.value.form.copy(campaign = campaign.value))
                }
            }
            val found = findOwn(creator, id)
            if (!active(account) || revision != formRevision) return@launchForOwner
            when (found) {
                is ApiResult.Failure -> formError(found.error, "No se pudo comprobar si ya postulaste. No se habilita un envío nuevo.")
                is ApiResult.Success -> if (found.value == null) {
                    if (ui.value.form.application != null) formError(malformed())
                    else if (ui.value.form.duplicateKnown) formError(ApiFailure(FailureKind.CONFLICT, "APPLICATION_ALREADY_EXISTS", "El servidor ya confirmó una postulación existente. Localízala en Mis postulaciones antes de continuar."))
                    else setForm(ui.value.form.copy(loading = false, checkedAbsence = true,
                        notice = if (ui.value.form.submission != null) "No se encontró la postulación todavía. Si reintentas, se conservarán la misma clave y los mismos datos." else null))
                } else {
                    val detail = applications.getDetails(found.value.id)
                    if (!active(account) || revision != formRevision) return@launchForOwner
                    when (detail) {
                        is ApiResult.Failure -> formError(detail.error)
                        is ApiResult.Success -> if (!detail.value.belongsTo(creator, id, found.value.id)) formError(malformed())
                            else setForm(bindExisting(ui.value.form, detail.value).copy(loading = false,
                                notice = "Tu postulación existente fue consultada. No se enviará otra."))
                    }
                }
            }
        }
    }
    private fun bindExisting(previous: OwnApplicationFormUiState, remote: Application): OwnApplicationFormUiState {
        val base = previous.application
        if (base != null && base.id == remote.id) return previous.copy(submission = null, checkedAbsence = false, latest = remote.takeIf { it != base })
        val message = previous.proposal.message.takeIf { it.isNotBlank() } ?: remote.message
        return previous.copy(application = remote, latest = null, checkedAbsence = false, submission = null,
            proposal = ApplicationProposal(message, remote.confirmedRequirementIds))
    }
    fun editMessage(message: String) {
        val state = ui.value.form
        if (!active() || state.locked) return
        setForm(state.copy(proposal = state.proposal.copy(message = message), failure = null, notice = null))
    }
    fun confirmRequirement(id: UUID, confirmed: Boolean) {
        val state = ui.value.form
        if (!active() || state.locked || state.application != null || state.campaign?.requirements?.none { it.id == id && it.ruleType == RequirementRule.MANUAL_CONFIRMATION } != false) return
        setForm(state.copy(proposal = state.proposal.copy(confirmations = if (confirmed) state.proposal.confirmations + id else state.proposal.confirmations - id), failure = null))
    }
    fun submit() {
        val state = ui.value.form; val id = state.campaignId ?: return; val campaign = state.campaign ?: return
        if (!active() || operationJob?.isActive == true || state.loading || state.busy || state.application != null || !state.checkedAbsence) return
        val intent = state.submission ?: run {
            state.proposal.validation(campaign)?.let { formError(it); return }
            if (campaign.summary.availability(clock.instant()) != CampaignAvailability.AVAILABLE) { formError(ApiFailure(FailureKind.CONFLICT,
                "CAMPAIGN_NOT_ACCEPTING_APPLICATIONS", "Esta campaña no admite nuevas postulaciones.")); return }
            ApplicationSubmission(id, state.proposal.copy(message = state.proposal.message.trim()))
        }
        val account = generation; val creator = owner!!.profileId
        setForm(state.copy(busy = true, submission = intent, failure = null, notice = null))
        operationJob = launchForOwner {
            val response = applications.submit(id, intent.proposal.message, intent.proposal.confirmations, intent.key)
            if (!active(account)) return@launchForOwner
            when (response) {
                is ApiResult.Success -> {
                    val remote = response.value
                    if (!remote.belongsTo(creator, id) || remote.message != intent.proposal.message || remote.confirmedRequirementIds != intent.proposal.confirmations || remote.status != ApplicationStatus.PENDING) {
                        formError(malformed(), "El envío podría haberse registrado. Verifica antes de modificar la propuesta.")
                    } else {
                        setForm(ui.value.form.copy(application = remote, proposal = ApplicationProposal(remote.message, remote.confirmedRequirementIds), submission = null,
                            checkedAbsence = false, busy = false, notice = "Envío confirmado por el servidor."))
                        showConfirmed(remote, "Envío confirmado. Actualiza el detalle para consultar su estado actual.")
                        // An idempotent POST replay can contain the original snapshot, not today's status.
                        val current = applications.getDetails(remote.id)
                        if (!active(account)) return@launchForOwner
                        val detail = when (current) {
                            is ApiResult.Failure -> OwnApplicationDetailUiState(remote.id, failure = current.error, notice = "El envío sí fue confirmado. No se pudo consultar su estado actual; actualiza el detalle.")
                            is ApiResult.Success -> if (!current.value.belongsTo(creator, id, remote.id)) OwnApplicationDetailUiState(remote.id, failure = malformed())
                                else { recordRemote(current.value); OwnApplicationDetailUiState(remote.id, current.value, notice = "Postulación registrada y estado actual consultado.") }
                        }
                        mutableUi.update { it.copy(detail = detail) }
                    }
                }
                is ApiResult.Failure -> {
                    if (response.error.code == "APPLICATION_ALREADY_EXISTS") {
                        resolveDuplicate(account, creator, id, response.error)
                    } else {
                        val uncertain = response.error.kind in setOf(FailureKind.NETWORK, FailureKind.TIMEOUT, FailureKind.SERVER, FailureKind.MALFORMED_RESPONSE) || response.error.code == "IDEMPOTENCY_KEY_REUSED"
                        setForm(ui.value.form.copy(busy = false, submission = intent.takeIf { uncertain }, failure = response.error,
                            notice = if (uncertain) "Envío sin confirmar. Verifica o reintenta con la misma clave y datos; tu propuesta se conserva." else "Tu propuesta no se perdió. Corrige el problema antes de volver a enviar."))
                    }
                }
            }
        }
    }
    private suspend fun resolveDuplicate(account: Long, creator: UUID, campaign: UUID, failure: ApiFailure) {
        setForm(ui.value.form.copy(checkedAbsence = false, duplicateKnown = true))
        when (val found = findOwn(creator, campaign)) {
            is ApiResult.Failure -> if (active(account)) formError(found.error, "El servidor indica que ya postulaste. No se pudo localizarla todavía; verifica o abre Mis postulaciones.")
            is ApiResult.Success -> if (active(account)) {
                if (found.value == null) formError(failure, "Ya existe una postulación, pero no se localizó en la consulta. No se enviará una nueva automáticamente.")
                else {
                    val detail = applications.getDetails(found.value.id)
                    if (!active(account)) return
                    when (detail) {
                        is ApiResult.Failure -> formError(detail.error)
                        is ApiResult.Success -> if (!detail.value.belongsTo(creator, campaign, found.value.id)) formError(malformed()) else {
                            setForm(bindExisting(ui.value.form, detail.value).copy(busy = false, failure = null, notice = "Ya habías postulado. Tu texto local se conserva como edición sin guardar."))
                            showConfirmed(detail.value, "Esta es tu postulación existente; no se creó una duplicada.")
                        }
                    }
                }
            }
        }
    }
    fun saveMessage() {
        val state = ui.value.form; val original = state.application ?: return
        if (!active() || state.locked || operationJob?.isActive == true || original.status != ApplicationStatus.PENDING) return
        state.proposal.validation()?.let { formError(it); return }
        write(ApplicationWrite(ApplicationWriteKind.UPDATE, original, state.proposal.message.trim()))
    }
    fun cancelSelected() {
        val original = ui.value.detail.application ?: return
        if (!active() || original.status != ApplicationStatus.PENDING || ui.value.detail.loading || operationJob?.isActive == true || forms[original.campaignId]?.pendingWrite != null) return
        val previous = forms[original.campaignId] ?: OwnApplicationFormUiState(campaignId = original.campaignId, proposal = ApplicationProposal(original.message, original.confirmedRequirementIds))
        setForm(previous.copy(application = original, latest = null, submission = null, loading = false))
        write(ApplicationWrite(ApplicationWriteKind.CANCEL, original))
    }
    private fun write(intent: ApplicationWrite) {
        val account = generation; val creator = owner!!.profileId
        detailJob?.cancel(); detailRevision++
        setForm(ui.value.form.copy(busy = true, pendingWrite = intent, failure = null, notice = null))
        mutableUi.update { it.copy(detail = it.detail.copy(busy = true, failure = null, notice = null)) }
        operationJob = launchForOwner {
            val result = if (intent.kind == ApplicationWriteKind.UPDATE) applications.updateMessage(intent.original.id, intent.message, intent.original.version)
                else applications.cancel(intent.original.id, intent.original.version)
            if (!active(account)) return@launchForOwner
            when (result) {
                is ApiResult.Success -> if (result.value.belongsTo(creator, intent.original.campaignId, intent.original.id) && intent.isConfirmedBy(result.value)) completeWrite(result.value, intent)
                    else { formError(malformed(), "No se pudo confirmar el resultado. Verifica el servidor antes de cambiar la propuesta."); setDetailError(malformed()) }
                is ApiResult.Failure -> {
                    val error = result.error
                    val reconcile = error.kind in setOf(FailureKind.NETWORK, FailureKind.TIMEOUT, FailureKind.SERVER, FailureKind.MALFORMED_RESPONSE, FailureKind.CONFLICT)
                    if (reconcile) reconcileWrite(intent, account, creator, error)
                    else { setForm(ui.value.form.copy(busy = false, pendingWrite = null, failure = error)); setDetailError(error) }
                }
            }
        }
    }
    private suspend fun reconcileWrite(intent: ApplicationWrite, account: Long, creator: UUID, failure: ApiFailure?) {
        val response = applications.getDetails(intent.original.id)
        if (!active(account)) return
        when (response) {
            is ApiResult.Failure -> { formError(response.error, "Tu texto se conserva. La operación sigue sin confirmar; vuelve a verificarla."); setDetailError(response.error) }
            is ApiResult.Success -> {
                val remote = response.value
                if (!remote.belongsTo(creator, intent.original.campaignId, intent.original.id)) { formError(malformed()); setDetailError(malformed()); return }
                if (intent.isConfirmedBy(remote) && failure?.code != "CONCURRENT_UPDATE") completeWrite(remote, intent, "El resultado fue verificado mediante consulta al servidor.")
                else {
                    val changed = remote != intent.original
                    setForm(ui.value.form.copy(busy = false, pendingWrite = null, latest = remote.takeIf { changed },
                        failure = failure, notice = if (changed) "La versión del servidor cambió. Compara ambas propuestas; no se sobrescribió tu texto local." else "El servidor conserva la versión original. Puedes reintentar con esa misma versión."))
                    mutableUi.update { it.copy(detail = OwnApplicationDetailUiState(remote.id, remote, failure = failure,
                        notice = "Estado consultado en el servidor. La propuesta local se conserva.")) }
                }
            }
        }
    }
    private fun completeWrite(remote: Application, intent: ApplicationWrite, notice: String? = null) {
        val proposal = if (intent.kind == ApplicationWriteKind.UPDATE) ApplicationProposal(remote.message, remote.confirmedRequirementIds) else ui.value.form.proposal
        setForm(ui.value.form.copy(application = remote, latest = null, proposal = proposal, busy = false, pendingWrite = null, failure = null,
            notice = notice ?: if (intent.kind == ApplicationWriteKind.UPDATE) "Propuesta actualizada." else "Postulación cancelada; el registro se conserva."))
        showConfirmed(remote, notice ?: if (intent.kind == ApplicationWriteKind.UPDATE) "Cambios guardados." else "Cancelación confirmada; no puedes volver a postular a esta misma campaña.")
    }
    private fun showConfirmed(remote: Application, notice: String) {
        mutableUi.update { it.copy(detail = OwnApplicationDetailUiState(remote.id, remote, notice = notice), navigate = OwnApplicationView.DETAIL) }
        loadList(ui.value.list.page?.page ?: 0, force = true)
    }
    private fun setDetailError(error: ApiFailure) { mutableUi.update { it.copy(detail = it.detail.copy(busy = false, failure = error,
        pendingOperation = it.form.pendingWrite != null)) } }
    fun verifySelected() {
        val selected = ui.value.detail.application
        val pending = selected?.let { forms[it.campaignId] }
        if (pending?.pendingWrite != null && active() && operationJob?.isActive != true) { setForm(pending); verifyForm() }
        else refreshDetail()
    }
    fun verifyForm() {
        val state = ui.value.form
        if (!active() || operationJob?.isActive == true || state.campaignId == null) return
        val pending = state.pendingWrite
        if (pending != null) {
            val account = generation; val creator = owner!!.profileId; setForm(state.copy(busy = true))
            operationJob = launchForOwner { reconcileWrite(pending, account, creator, null) }
        } else { setForm(state.copy(loading = true, checkedAbsence = false, failure = null)); loadForm() }
    }
    /** Explicit user choice after reading both versions; never an automatic retry on a fresh version. */
    fun useLatest(keepLocal: Boolean) {
        val state = ui.value.form; val remote = state.latest ?: return
        if (!active() || state.busy || state.loading || (keepLocal && remote.status != ApplicationStatus.PENDING)) return
        setForm(state.copy(application = remote, latest = null, pendingWrite = null,
            proposal = ApplicationProposal(if (keepLocal) state.proposal.message else remote.message, remote.confirmedRequirementIds), failure = null,
            notice = if (keepLocal) "Tu texto se conserva usando la versión ${remote.version} como base. Revísalo antes de guardar." else "Se cargó la propuesta actual del servidor."))
    }
    private fun malformed() = ApiFailure(FailureKind.MALFORMED_RESPONSE, message = "La respuesta de postulaciones no corresponde a tu cuenta o a la operación solicitada.")
}
