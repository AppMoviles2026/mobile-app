package com.example.collabpro.features.campaign.presentation.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.drafts.*
import com.example.collabpro.features.campaign.application.usecases.CampaignUseCases
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.identity.application.auth.*
import com.example.collabpro.features.identity.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.util.UUID
import com.example.collabpro.core.application.security.ExpectedAccount
import javax.inject.Inject

@HiltViewModel
class BrandCampaignViewModel @Inject constructor(private val useCases: CampaignUseCases, private val preparation: CampaignPreparation,
    private val drafts: CampaignDraftStore, private val authentication: AuthenticationSession, private val clock: Clock) : ViewModel() {
    private val mutableUi = MutableStateFlow(BrandCampaignUiState())
    val ui = mutableUi.asStateFlow()
    private var owner: Account? = null
    private var expiry: java.time.Instant? = null
    private var generation = 0L
    private var listRevision = 0L
    private var detailRevision = 0L
    private var editRevision = 0L
    private var editorJob: Job? = null
    private var persistJob: Job? = null
    private var listJob: Job? = null
    private var detailJob: Job? = null
    init { viewModelScope.launch { authentication.state.collect { state ->
        val current = state as? SessionState.Authenticated
        if (current?.account?.accountType != AccountType.BRAND) {
            if (owner != null) { cancelRequests(); owner = null; expiry = null; mutableUi.value = BrandCampaignUiState() }
        } else if (owner?.accountId != current.account.accountId || expiry != current.expiresAt) {
            cancelRequests(); owner = current.account; expiry = current.expiresAt
            mutableUi.value = BrandCampaignUiState(ownerId = current.account.accountId)
            val revision = generation
            editorJob = launchForOwner launch@ {
                when (val loaded = drafts.load(current.account.accountId)) {
                    is ApiResult.Failure -> if (active(revision)) mutableUi.update { it.copy(editor = CampaignEditorUiState(restoring = false, storageFailure = loaded.error)) }
                    is ApiResult.Success -> if (active(revision)) {
                        mutableUi.update { it.copy(editor = CampaignEditorUiState(draft = loaded.value ?: CampaignDraft(), restoring = false,
                            notice = if (loaded.value != null) "Preparación local restaurada. Verifica el servidor antes de continuar." else null)) }
                        if (loaded.value?.serverId != null) reconcile(revision)
                    }
                }
            }
        } else owner = current.account
    } } }
    private fun cancelRequests() {
        generation++; listRevision++; detailRevision++; editRevision++
        editorJob?.cancel(); persistJob?.cancel(); listJob?.cancel(); detailJob?.cancel()
        editorJob = null; persistJob = null; listJob = null; detailJob = null
    }
    private fun active(revision: Long = generation): Boolean {
        val session = authentication.state.value as? SessionState.Authenticated ?: return false
        return revision == generation && owner != null && session.account.accountId == owner?.accountId &&
            session.account.accountType == AccountType.BRAND && session.expiresAt == expiry
    }
    private fun launchForOwner(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(ExpectedAccount(owner!!.accountId, expiry!!), block = block)
    fun consumeNavigation() { mutableUi.update { it.copy(navigate = null) } }
    fun openEditor() {
        if (!active() || ui.value.editor.restoring) return
        mutableUi.update { it.copy(navigate = if (it.editor.draft?.step == 2) CampaignView.TERMS else CampaignView.BASICS) }
    }
    fun editBasics(update: CampaignBasics.() -> CampaignBasics) {
        val state = ui.value.editor
        val draft = state.draft ?: return
        if (!active() || state.busy || state.metadataLocked || state.restoring) return
        edited(draft.copy(basics = draft.basics.update()))
    }
    fun editConditions(update: ConditionsDraft.() -> ConditionsDraft) {
        val state = ui.value.editor
        val draft = state.draft ?: return
        if (!active() || state.conditionsLocked) return
        edited(draft.copy(conditions = draft.conditions.update(), conditionsSaved = false))
    }
    private fun edited(draft: CampaignDraft) {
        editRevision++
        mutableUi.update { it.copy(editor = it.editor.copy(draft = draft, failure = null, notice = null, localSaving = true)) }
        persistJob?.cancel()
        val revision = generation; val edit = editRevision; val accountId = owner!!.accountId
        persistJob = launchForOwner launch@ {
            delay(300)
            val saved = drafts.save(accountId, draft)
            if (active(revision) && edit == editRevision) mutableUi.update { it.copy(editor = it.editor.copy(localSaving = false,
                storageFailure = (saved as? ApiResult.Failure)?.error)) }
        }
    }
    fun addRequirement() = editConditions { if (requirements.size < 50) copy(requirements = requirements + RequirementDraft()) else this }
    fun addDeliverable() = editConditions { if (deliverables.size < 50) copy(deliverables = deliverables + DeliverableDraft()) else this }
    fun continueToConditions() {
        val editor = ui.value.editor; val draft = editor.draft ?: return
        if (!active() || editor.busy || editor.restoring) return
        CampaignDraftValidation.basics(draft.basics)?.let { error -> mutableUi.update { it.copy(editor = it.editor.copy(failure = error)) }; return }
        edited(draft.copy(step = 2))
        mutableUi.update { it.copy(navigate = CampaignView.TERMS) }
    }
    fun backToBasics() {
        val draft = ui.value.editor.draft ?: return
        if (!active() || ui.value.editor.busy) return
        edited(draft.copy(step = 1)); mutableUi.update { it.copy(navigate = CampaignView.BASICS) }
    }
    fun prepare(goal: PreparationGoal) {
        val account = owner ?: return; val draft = ui.value.editor.draft ?: return
        if (!active() || editorJob?.isActive == true || ui.value.editor.readOnly) return
        persistJob?.cancel(); persistJob = null
        val revision = generation
        mutableUi.update { it.copy(editor = it.editor.copy(busy = true, failure = null, notice = null, localSaving = false)) }
        editorJob = launchForOwner launch@ {
            val result = preparation.execute(account.profileId, draft, goal) { next, details -> checkpoint(revision, next, details) }
            if (!active(revision)) return@launch
            mutableUi.update { it.copy(editor = it.editor.copy(busy = false,
                failure = (result as? ApiResult.Failure)?.error,
                notice = if (result is ApiResult.Success) {
                    if (result.value.summary.status == CampaignStatus.OPEN) "Publicación confirmada por el servidor. La campaña ya es visible para creadores."
                    else if (goal == PreparationGoal.CONDITIONS) "Condiciones guardadas. La campaña sigue en borrador, sin publicarse."
                    else "Borrador creado en el servidor. Completa las condiciones antes de publicar."
                } else partialNotice())) }
            if (result is ApiResult.Success) loadCampaigns(ui.value.campaigns.page?.page ?: 0)
        }
    }
    private suspend fun checkpoint(revision: Long, next: CampaignDraft, details: CampaignDetails?): ApiResult<Unit> {
        if (!active(revision)) return changed()
        mutableUi.update { it.copy(editor = it.editor.copy(draft = next, details = details ?: it.editor.details)) }
        val result = drafts.save(owner!!.accountId, next)
        if (!active(revision)) return changed()
        mutableUi.update { it.copy(editor = it.editor.copy(storageFailure = (result as? ApiResult.Failure)?.error)) }
        return result
    }
    private fun partialNotice(): String? {
        val draft = ui.value.editor.draft ?: return null
        return when {
            ui.value.editor.details?.summary?.status == CampaignStatus.OPEN -> "La publicación sí fue confirmada por el servidor, pero queda un error local. Comprueba o recarga para conservar su estado en este dispositivo."
            draft.pending == DraftOperation.CREATE && draft.serverId == null -> "Creación sin confirmar. Reintenta con la misma clave y datos; no se iniciará otra campaña."
            draft.pending == DraftOperation.PUBLISH -> "Las condiciones se conservaron. La publicación no está confirmada: comprueba el servidor."
            draft.serverId != null -> "El borrador ${draft.serverId} se conserva. Los cambios locales pendientes no se presentan como guardados en el servidor."
            else -> null
        }
    }
    fun checkServer() {
        if (!active() || editorJob?.isActive == true || ui.value.editor.draft?.serverId == null) return
        persistJob?.cancel(); persistJob = null
        val revision = generation
        editorJob = launchForOwner launch@ { reconcile(revision) }
    }
    private suspend fun reconcile(revision: Long) {
        val draft = ui.value.editor.draft ?: return
        val id = draft.serverId ?: return
        mutableUi.update { it.copy(editor = it.editor.copy(busy = true, failure = null)) }
        when (val checked = useCases.getDetails(id)) {
            is ApiResult.Failure -> if (active(revision)) mutableUi.update { it.copy(editor = it.editor.copy(busy = false, failure = checked.error)) }
            is ApiResult.Success -> if (active(revision)) {
                val details = checked.value
                if (!owned(details, id)) { mutableUi.update { it.copy(editor = it.editor.copy(busy = false, failure = malformed())) }; return }
                val parsed = CampaignDraftValidation.conditions(draft.conditions, clock.instant())
                val next = draft.copy(pending = null, conditionsSaved = parsed is ApiResult.Success && details.matches(parsed.value))
                val saved = checkpoint(revision, next, details)
                if (active(revision)) mutableUi.update { it.copy(editor = it.editor.copy(busy = false,
                    failure = (saved as? ApiResult.Failure)?.error,
                    notice = "Estado verificado: ${details.summary.status.name}. La edición local se conservó; recarga si deseas reemplazarla por los datos del servidor.")) }
            }
        }
    }
    fun reloadEditor() {
        if (!active() || editorJob?.isActive == true) return
        val draft = ui.value.editor.draft ?: return; val id = draft.serverId ?: return; val revision = generation
        persistJob?.cancel()
        mutableUi.update { it.copy(editor = it.editor.copy(busy = true)) }
        editorJob = launchForOwner launch@ {
            val result = useCases.getDetails(id)
            if (!active(revision)) return@launch
            if (result is ApiResult.Success && owned(result.value, id)) {
                val stored = checkpoint(revision, result.value.toDraft(draft.conditions.zoneId), result.value)
                if (active(revision)) mutableUi.update { it.copy(editor = it.editor.copy(busy = false, failure = (stored as? ApiResult.Failure)?.error,
                    notice = "Se reemplazó la edición local por el borrador del servidor.")) }
            } else mutableUi.update { it.copy(editor = it.editor.copy(busy = false, failure = (result as? ApiResult.Failure)?.error ?: malformed())) }
        }
    }
    fun newDraft() {
        if (!active() || editorJob?.isActive == true || ui.value.editor.busy) return
        if (ui.value.editor.draft?.let { it.serverId == null && it.creation != null } == true) {
            mutableUi.update { it.copy(editor = it.editor.copy(failure = ApiFailure(FailureKind.CONFLICT,
                message = "Resuelve la creación pendiente antes de iniciar otra campaña; podría existir en el servidor."))) }; return
        }
        persistJob?.cancel(); val revision = generation; val accountId = owner!!.accountId
        mutableUi.update { it.copy(editor = it.editor.copy(busy = true)) }
        editorJob = launchForOwner launch@ {
            val cleared = drafts.clear(accountId)
            if (!active(revision)) return@launch
            if (cleared is ApiResult.Failure) mutableUi.update { it.copy(editor = it.editor.copy(busy = false, storageFailure = cleared.error)) }
            else { val fresh = CampaignDraft(); mutableUi.update { it.copy(editor = CampaignEditorUiState(draft = fresh, restoring = false), navigate = CampaignView.BASICS) } }
        }
    }
    fun loadCampaigns(page: Int = 0) {
        if (!active()) return
        listJob?.cancel(); val revision = generation; val request = ++listRevision
        mutableUi.update { it.copy(campaigns = it.campaigns.copy(loading = true, failure = null)) }
        listJob = launchForOwner launch@ {
            val result = useCases.getOwn(PageRequest(page))
            if (!active(revision) || request != listRevision) return@launch
            mutableUi.update { state -> state.copy(campaigns = when (result) {
                is ApiResult.Failure -> state.campaigns.copy(loading = false, failure = result.error)
                is ApiResult.Success -> if (result.value.page != page || result.value.items.any { it.brandId != owner!!.profileId })
                    state.campaigns.copy(loading = false, failure = malformed())
                else OwnCampaignsUiState(page = result.value, notice = state.campaigns.notice)
            }) }
        }
    }
    fun openDetails(id: UUID) {
        if (!active() || ui.value.detail.busy) return
        detailJob?.cancel(); val revision = generation; val request = ++detailRevision
        mutableUi.update { it.copy(detail = OwnedCampaignDetailUiState(id = id, loading = true), navigate = CampaignView.DETAIL) }
        detailJob = launchForOwner launch@ {
            val result = useCases.getDetails(id)
            if (!active(revision) || request != detailRevision) return@launch
            mutableUi.update { it.copy(detail = when (result) {
                is ApiResult.Failure -> OwnedCampaignDetailUiState(id = id, failure = result.error)
                is ApiResult.Success -> if (owned(result.value, id)) OwnedCampaignDetailUiState(id = id, details = result.value)
                    else OwnedCampaignDetailUiState(id = id, failure = malformed())
            }) }
        }
    }
    fun editSelected() {
        val details = ui.value.detail.details ?: return
        if (!active() || editorJob?.isActive == true || details.summary.status != CampaignStatus.DRAFT) return
        val existing = ui.value.editor.draft
        if (existing?.creation != null && existing.serverId == null && existing.basics.request() != details.toDraft().basics.request()) {
            mutableUi.update { it.copy(detail = it.detail.copy(failure = ApiFailure(FailureKind.CONFLICT,
                message = "Hay una creación pendiente con otros datos. Primero resuelve ese intento."))) }; return
        }
        persistJob?.cancel(); val revision = generation
        mutableUi.update { it.copy(editor = it.editor.copy(busy = true)) }
        editorJob = launchForOwner launch@ {
            val next = if (existing?.serverId == details.summary.id || (existing?.creation != null && existing.serverId == null))
                existing.copy(serverId = details.summary.id, pending = null, step = 2) else details.toDraft()
            val saved = checkpoint(revision, next, details)
            if (active(revision)) mutableUi.update { it.copy(editor = it.editor.copy(busy = false, failure = (saved as? ApiResult.Failure)?.error),
                navigate = if (saved is ApiResult.Success) CampaignView.TERMS else null) }
        }
    }
    fun discardOrClose(discard: Boolean) {
        val details = ui.value.detail.details ?: return; val id = details.summary.id
        if (!active() || detailJob?.isActive == true || editorJob?.isActive == true) return
        if ((discard && details.summary.status != CampaignStatus.DRAFT) || (!discard && details.summary.status != CampaignStatus.OPEN)) return
        val revision = generation
        mutableUi.update { it.copy(detail = it.detail.copy(busy = true, failure = null)) }
        detailJob = launchForOwner launch@ {
            val checked = useCases.getDetails(id)
            if (!active(revision)) return@launch
            if (checked !is ApiResult.Success || !owned(checked.value, id)) {
                mutableUi.update { it.copy(detail = it.detail.copy(busy = false, failure = (checked as? ApiResult.Failure)?.error ?: malformed())) }; return@launch
            }
            if (discard && checked.value.summary.status != CampaignStatus.DRAFT) {
                mutableUi.update { it.copy(detail = it.detail.copy(details = checked.value, busy = false, failure = ApiFailure(FailureKind.CONFLICT, message = "La campaña ya no es un borrador."))) }; return@launch
            }
            val result = if (discard) useCases.discardDraft(id) else useCases.close(id)
            if (!active(revision)) return@launch
            if (result is ApiResult.Failure) {
                mutableUi.update { it.copy(detail = it.detail.copy(busy = false, failure = result.error, notice = "Resultado no confirmado. Actualiza el detalle antes de repetir la operación.")) }
            } else {
                if (!discard) {
                    val closed = (result as ApiResult.Success).value as CampaignDetails
                    if (!owned(closed, id) || closed.summary.status != CampaignStatus.CLOSED) {
                        mutableUi.update { it.copy(detail = it.detail.copy(busy = false, failure = malformed())) }; return@launch
                    }
                    mutableUi.update { it.copy(detail = it.detail.copy(details = closed, busy = false, notice = "Postulaciones cerradas. La campaña y sus postulaciones se conservan.")) }
                    if (ui.value.editor.draft?.serverId == id) checkpoint(revision, ui.value.editor.draft!!.copy(pending = null), closed)
                } else {
                    if (ui.value.editor.draft?.serverId == id) {
                        persistJob?.cancel()
                        val cleared = drafts.clear(owner!!.accountId)
                        if (!active(revision)) return@launch
                        mutableUi.update { it.copy(editor = if (cleared is ApiResult.Success)
                            CampaignEditorUiState(draft = CampaignDraft(), restoring = false)
                            else it.editor.copy(storageFailure = (cleared as ApiResult.Failure).error)) }
                    }
                    mutableUi.update { it.copy(detail = OwnedCampaignDetailUiState(), navigate = CampaignView.LIST,
                        campaigns = it.campaigns.copy(notice = "Borrador eliminado del servidor; no se puede recuperar.")) }
                }
                loadCampaigns()
            }
        }
    }
    private fun owned(value: CampaignDetails, id: UUID) = value.summary.id == id && value.summary.brandId == owner?.profileId
    private fun malformed() = ApiFailure(FailureKind.MALFORMED_RESPONSE, message = "El servidor devolvió una campaña o página diferente a la solicitada.")
    private fun changed() = ApiResult.Failure(ApiFailure(FailureKind.SESSION_CHANGED, message = "La sesión cambió; se descartó la operación anterior."))
}
