package com.example.collabpro.features.campaign.application.usecases

import com.example.collabpro.core.domain.ApiFailure
import com.example.collabpro.core.domain.ApiResult
import com.example.collabpro.core.domain.FailureKind
import com.example.collabpro.features.campaign.domain.model.Applicant
import com.example.collabpro.features.campaign.domain.model.ApplicantDecision
import com.example.collabpro.features.campaign.domain.model.ApplicationStatus
import com.example.collabpro.features.campaign.domain.repositories.ApplicantEvaluationRepository

class EvaluateApplication(private val repository: ApplicantEvaluationRepository) {
    suspend operator fun invoke(candidate: Applicant, decision: ApplicantDecision, reason: String): ApiResult<Applicant> {
        val application = candidate.application
        if (application.status != ApplicationStatus.PENDING)
            return ApiResult.Failure(ApiFailure(FailureKind.CONFLICT, message = "La postulación ya no está pendiente."))
        val note = reason.trim()
        if (note.length > 2000 || (decision == ApplicantDecision.REJECTED && note.isBlank()))
            return ApiResult.Failure(ApiFailure(FailureKind.VALIDATION, message = "Escribe un motivo de hasta 2000 caracteres."))
        return repository.decide(application.campaignId, application.id, decision, note.ifEmpty { null }, application.version)
    }
}
