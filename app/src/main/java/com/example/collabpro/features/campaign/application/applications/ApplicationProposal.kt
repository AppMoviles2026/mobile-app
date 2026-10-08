package com.example.collabpro.features.campaign.application.applications

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.domain.model.*
import java.util.UUID

data class ApplicationProposal(val message: String = "", val confirmations: Set<UUID> = emptySet()) {
    fun validation(campaign: CampaignDetails? = null): ApiFailure? {
        val fields = buildMap {
            if (message.isBlank()) put("message", "Escribe tu propuesta.")
            else if (message.trim().length > 4000) put("message", "Usa como máximo 4000 caracteres.")
            if (confirmations.size > 50) put("confirmedRequirementIds", "No puedes confirmar más de 50 requisitos.")
            if (campaign != null) {
                val manual = campaign.requirements.filter { it.ruleType == RequirementRule.MANUAL_CONFIRMATION }
                if (!manual.map { it.id }.toSet().containsAll(confirmations)) put("confirmedRequirementIds", "Solo puedes confirmar requisitos manuales de esta campaña.")
                manual.filter { it.mandatory && it.id !in confirmations }.forEach { put("requirements.${it.id}", "Confirma este requisito obligatorio.") }
            }
        }
        return if (fields.isEmpty()) null else ApiFailure(FailureKind.VALIDATION, message = "Revisa tu propuesta y las confirmaciones.", fieldErrors = fields)
    }
}

/** One immutable body and one key per submit intent, not per network attempt. */
data class ApplicationSubmission(val campaignId: UUID, val proposal: ApplicationProposal, val key: IdempotencyKey = IdempotencyKey())
enum class ApplicationWriteKind { UPDATE, CANCEL }
data class ApplicationWrite(val kind: ApplicationWriteKind, val original: Application, val message: String = original.message)

fun Application.belongsTo(creatorId: UUID, campaignId: UUID? = null, id: UUID? = null): Boolean =
    this.creatorId == creatorId && (campaignId == null || this.campaignId == campaignId) && (id == null || this.id == id) &&
        version >= 0 && message.isNotBlank() && message.length <= 4000 && confirmedRequirementIds.size <= 50

fun ApplicationWrite.isConfirmedBy(current: Application): Boolean = current.id == original.id && current.campaignId == original.campaignId &&
    current.creatorId == original.creatorId && current.confirmedRequirementIds == original.confirmedRequirementIds && current.submittedAt == original.submittedAt &&
    current.version >= original.version && when (kind) {
        ApplicationWriteKind.UPDATE -> current.status == ApplicationStatus.PENDING && current.message == message &&
            (message == original.message || current.version > original.version)
        ApplicationWriteKind.CANCEL -> current.status == ApplicationStatus.CANCELLED && current.version > original.version && current.message == original.message
    }
