package com.example.collabpro.features.campaign.presentation.manage

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.drafts.CampaignDraft
import com.example.collabpro.features.campaign.domain.model.*
import java.util.UUID

enum class CampaignView { LIST, BASICS, TERMS, DETAIL }
data class CampaignEditorUiState(val draft: CampaignDraft? = null, val details: CampaignDetails? = null,
    val restoring: Boolean = true, val busy: Boolean = false, val localSaving: Boolean = false,
    val failure: ApiFailure? = null, val storageFailure: ApiFailure? = null, val notice: String? = null) {
    val metadataLocked get() = draft?.serverId != null || draft?.creation != null
    val readOnly get() = details?.summary?.status?.let { it != CampaignStatus.DRAFT } == true
    val conditionsLocked get() = busy || restoring || readOnly || draft?.pending != null || (draft?.serverId != null && details == null)
}
data class OwnCampaignsUiState(val page: Page<CampaignSummary>? = null, val loading: Boolean = false,
    val failure: ApiFailure? = null, val notice: String? = null)
data class OwnedCampaignDetailUiState(val id: UUID? = null, val details: CampaignDetails? = null,
    val loading: Boolean = false, val busy: Boolean = false, val failure: ApiFailure? = null, val notice: String? = null)
data class BrandCampaignUiState(val editor: CampaignEditorUiState = CampaignEditorUiState(),
    val campaigns: OwnCampaignsUiState = OwnCampaignsUiState(), val detail: OwnedCampaignDetailUiState = OwnedCampaignDetailUiState(),
    val navigate: CampaignView? = null, val ownerId: UUID? = null, val expiresAt: java.time.Instant? = null)
