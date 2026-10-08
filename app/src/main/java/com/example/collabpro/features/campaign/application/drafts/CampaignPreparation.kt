package com.example.collabpro.features.campaign.application.drafts

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.domain.repositories.CampaignRepository
import java.time.Clock
import java.time.Duration
import java.util.UUID

enum class PreparationGoal { BASIC_DRAFT, CONDITIONS, PUBLICATION }

/** Sequential business workflow. Every durable checkpoint precedes the next external write. */
class CampaignPreparation(private val repository: CampaignRepository, private val clock: Clock) {
    suspend fun execute(brandId: UUID, original: CampaignDraft, goal: PreparationGoal,
        checkpoint: suspend (CampaignDraft, CampaignDetails?) -> ApiResult<Unit>): ApiResult<CampaignDetails> {
        var draft = original
        CampaignDraftValidation.basics(draft.basics)?.let { return ApiResult.Failure(it) }
        var conditions = if (goal == PreparationGoal.BASIC_DRAFT || draft.serverId != null) null else when (val result = CampaignDraftValidation.conditions(draft.conditions, clock.instant())) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }
        suspend fun remember(next: CampaignDraft, details: CampaignDetails? = null): ApiResult<Unit> {
            draft = next
            return checkpoint(next, details)
        }
        suspend fun failed(error: ApiFailure, operation: DraftOperation): ApiResult.Failure {
            val definitive = error.httpStatus in listOf(400, 403, 404, 422) || error.code == "CLIENT_VALIDATION"
            if (definitive) remember(draft.copy(pending = null,
                creation = if (operation == DraftOperation.CREATE) null else draft.creation))
            return ApiResult.Failure(error)
        }
        if (draft.serverId == null) {
            val intent = draft.creation ?: CreationIntent(draft.basics.request(), IdempotencyKey(), clock.instant())
            if (intent.request != draft.basics.request()) return failure("No cambies los metadatos de una creación pendiente; primero verifica el borrador.")
            if (Duration.between(intent.startedAt, clock.instant()) >= Duration.ofHours(24))
                return failure("La garantía de reintento venció (24 horas). Busca la campaña en Mis campañas antes de iniciar otra creación.")
            val saved = remember(draft.copy(creation = intent, pending = DraftOperation.CREATE))
            if (saved is ApiResult.Failure) return saved
            when (val created = repository.create(intent.request, intent.key)) {
                is ApiResult.Failure -> return failed(created.error, DraftOperation.CREATE)
                is ApiResult.Success -> {
                    val value = created.value
                    if (value.summary.brandId != brandId) return failure("La campaña devuelta no pertenece a esta empresa.", FailureKind.MALFORMED_RESPONSE)
                    val stored = remember(draft.copy(serverId = value.summary.id, pending = null), value)
                    if (stored is ApiResult.Failure) return stored
                }
            }
        }
        val id = draft.serverId!!
        // Creation replay can return an old cached snapshot. GET is authoritative before further writes.
        var details = when (val result = repository.details(id)) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }
        if (details.summary.id != id || details.summary.brandId != brandId)
            return failure("El servidor devolvió una campaña diferente.", FailureKind.MALFORMED_RESPONSE)
        if (details.summary.status != CampaignStatus.DRAFT) {
            val confirmed = remember(draft.copy(pending = null), details)
            if (confirmed is ApiResult.Failure) return confirmed
            return if (details.summary.status == CampaignStatus.OPEN) ApiResult.Success(details)
                else failure("La campaña está ${details.summary.status.name} y no se puede preparar ni publicar.", FailureKind.CONFLICT)
        }
        if (goal == PreparationGoal.BASIC_DRAFT) {
            val saved = remember(draft.copy(pending = null), details)
            return if (saved is ApiResult.Failure) saved else ApiResult.Success(details)
        }
        if (conditions == null) conditions = when (val result = CampaignDraftValidation.conditions(draft.conditions, clock.instant())) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }
        if (!details.matches(conditions!!)) {
            val saved = remember(draft.copy(pending = DraftOperation.CONDITIONS, conditionsSaved = false), details)
            if (saved is ApiResult.Failure) return saved
            when (val result = repository.saveConditions(id, conditions)) {
                is ApiResult.Failure -> return failed(result.error, DraftOperation.CONDITIONS)
                is ApiResult.Success -> {
                    if (result.value.summary.id != id || result.value.summary.brandId != brandId ||
                        result.value.summary.status != CampaignStatus.DRAFT || !result.value.matches(conditions))
                        return failure("No se pudo confirmar el guardado de estas condiciones.", FailureKind.MALFORMED_RESPONSE)
                    details = result.value
                }
            }
        }
        val stored = remember(draft.copy(pending = null, conditionsSaved = true), details)
        if (stored is ApiResult.Failure) return stored
        if (goal == PreparationGoal.CONDITIONS) return ApiResult.Success(details)
        val publishing = remember(draft.copy(pending = DraftOperation.PUBLISH), details)
        if (publishing is ApiResult.Failure) return publishing
        when (val result = repository.publish(id)) {
            is ApiResult.Success -> details = result.value
            is ApiResult.Failure -> {
                // A previous timed-out publication may already have committed; never pretend it failed or create another.
                if (result.error.kind == FailureKind.CONFLICT) {
                    val checked = repository.details(id)
                    if (checked is ApiResult.Success && checked.value.summary.status == CampaignStatus.OPEN) details = checked.value
                    else return failed(result.error, DraftOperation.PUBLISH)
                } else return failed(result.error, DraftOperation.PUBLISH)
            }
        }
        if (details.summary.id != id || details.summary.brandId != brandId || details.summary.status != CampaignStatus.OPEN)
            return failure("No se pudo confirmar la publicación de esta campaña.", FailureKind.MALFORMED_RESPONSE)
        val completed = remember(draft.copy(pending = null), details)
        return if (completed is ApiResult.Failure) completed else ApiResult.Success(details)
    }
    private fun failure(message: String, kind: FailureKind = FailureKind.CONFLICT) = ApiResult.Failure(ApiFailure(kind, message = message))
}
