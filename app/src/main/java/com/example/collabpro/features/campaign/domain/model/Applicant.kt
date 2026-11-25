package com.example.collabpro.features.campaign.domain.model

import java.util.UUID

enum class ApplicantDecision { SELECTED, REJECTED }

data class Applicant(
    val application: Application,
    val creator: ApplicantProfile?
)

data class ApplicantProfile(
    val id: UUID, val name: String, val biography: String?, val niche: String?,
    val audienceDescription: String?, val location: String?, val active: Boolean,
    val socialAccounts: List<ApplicantSocialAccount>
)

data class ApplicantSocialAccount(val id: UUID, val platform: String, val username: String)
