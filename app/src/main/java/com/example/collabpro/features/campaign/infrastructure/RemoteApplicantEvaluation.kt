package com.example.collabpro.features.campaign.infrastructure

import com.example.collabpro.core.domain.PageRequest
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.domain.repositories.ApplicantEvaluationRepository
import com.example.collabpro.features.campaign.infrastructure.remote.*
import java.util.UUID

internal class RemoteApplicantEvaluation(
    private val api: ApplicantEvaluationApi, private val executor: ApiExecutor
) : ApplicantEvaluationRepository {
    override suspend fun list(campaignId: UUID, page: PageRequest, status: ApplicationStatus?) =
        executor.protectedCall({ api.list(it, campaignId.toString(), page.page, page.size, status?.name) }) {
            it.toDomain { dto -> dto.toCandidate(campaignId) }.also { result ->
                if (result.page != page.page || (status != null && result.items.any { item -> item.application.status != status }))
                    throw InvalidApiResponse("Unexpected applicant page")
            }
        }

    override suspend fun detail(campaignId: UUID, applicationId: UUID) =
        executor.protectedCall({ api.detail(it, campaignId.toString(), applicationId.toString()) }) {
            it.toCandidate(campaignId, applicationId)
        }

    override suspend fun decide(campaignId: UUID, applicationId: UUID, decision: ApplicantDecision, reason: String?, expectedVersion: Long) =
        executor.protectedCall({
            api.decide(it, campaignId.toString(), applicationId.toString(), ApplicantDecisionDto(decision.name, reason, expectedVersion))
        }) {
            it.toCandidate(campaignId, applicationId).also { value ->
                if (value.application.status.name != decision.name || value.application.version <= expectedVersion)
                    throw InvalidApiResponse("Unexpected applicant decision")
            }
        }

    private fun ApplicantDto.toCandidate(campaignId: UUID, applicationId: UUID? = null): Applicant {
        val record = application.required("application").toDomain()
        val profile = creator?.let {
            ApplicantProfile(it.id.uuid("creator.id"), it.name.required("creator.name"), it.biography, it.niche,
                it.audienceDescription, it.location, it.active.required("creator.active"),
                it.socialAccounts.required("creator.socialAccounts").map { social ->
                    ApplicantSocialAccount(social.id.uuid("social.id"), social.platform.required("platform"), social.username.required("username"))
                })
        }
        if (record.campaignId != campaignId || (applicationId != null && record.id != applicationId) ||
            (profile != null && profile.id != record.creatorId)) throw InvalidApiResponse("Unexpected applicant identity")
        return Applicant(record, profile)
    }
}
