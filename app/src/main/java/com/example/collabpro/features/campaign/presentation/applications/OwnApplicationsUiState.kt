package com.example.collabpro.features.campaign.presentation.applications

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.applications.*
import com.example.collabpro.features.campaign.domain.model.*
import java.time.Instant
import java.util.UUID

enum class OwnApplicationView { LIST, FORM, DETAIL }
data class OwnApplicationsListUiState(val page: Page<Application>? = null, val requestedPage: Int = 0,
    val loading: Boolean = false, val failure: ApiFailure? = null) {
    val hasNext get() = page?.let { (it.page.toLong() + 1) * it.size < it.total } == true
    val hasPrevious get() = (page?.page ?: 0) > 0
}
data class OwnApplicationFormUiState(val campaignId: UUID? = null, val campaign: CampaignDetails? = null,
    val application: Application? = null, val latest: Application? = null, val proposal: ApplicationProposal = ApplicationProposal(),
    val checkedAbsence: Boolean = false, val duplicateKnown: Boolean = false, val loading: Boolean = false, val busy: Boolean = false,
    val submission: ApplicationSubmission? = null, val pendingWrite: ApplicationWrite? = null,
    val failure: ApiFailure? = null, val notice: String? = null) {
    val conflict get() = latest != null && latest != application
    val readOnly get() = application != null && application.status != ApplicationStatus.PENDING
    val locked get() = loading || busy || readOnly || conflict || submission != null || pendingWrite != null
}
data class OwnApplicationDetailUiState(val id: UUID? = null, val application: Application? = null,
    val loading: Boolean = false, val busy: Boolean = false, val failure: ApiFailure? = null, val notice: String? = null,
    val pendingOperation: Boolean = false)
data class OwnApplicationsUiState(val ownerId: UUID? = null, val expiresAt: Instant? = null,
    val list: OwnApplicationsListUiState = OwnApplicationsListUiState(), val form: OwnApplicationFormUiState = OwnApplicationFormUiState(),
    val detail: OwnApplicationDetailUiState = OwnApplicationDetailUiState(), val now: Instant = Instant.EPOCH,
    val navigate: OwnApplicationView? = null)
