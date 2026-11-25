package com.example.collabpro.features.campaign.domain.repositories

import com.example.collabpro.core.domain.ApiResult
import com.example.collabpro.core.domain.Page
import com.example.collabpro.core.domain.PageRequest
import com.example.collabpro.features.campaign.domain.model.Applicant
import com.example.collabpro.features.campaign.domain.model.ApplicantDecision
import com.example.collabpro.features.campaign.domain.model.ApplicationStatus
import java.util.UUID

interface ApplicantEvaluationRepository {
    suspend fun list(campaignId: UUID, page: PageRequest, status: ApplicationStatus?): ApiResult<Page<Applicant>>
    suspend fun detail(campaignId: UUID, applicationId: UUID): ApiResult<Applicant>
    suspend fun decide(campaignId: UUID, applicationId: UUID, decision: ApplicantDecision, reason: String?, expectedVersion: Long): ApiResult<Applicant>
}
