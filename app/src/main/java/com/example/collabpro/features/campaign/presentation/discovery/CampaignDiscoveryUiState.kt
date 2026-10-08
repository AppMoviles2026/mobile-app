package com.example.collabpro.features.campaign.presentation.discovery

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.discovery.DiscoveryFilters
import com.example.collabpro.features.campaign.domain.model.*
import java.time.Instant
import java.util.UUID

enum class DiscoveryView { HOME, SEARCH, DETAIL }
data class DiscoveryPageUiState(val page: Page<CampaignSummary>? = null, val loading: Boolean = false,
    val failure: ApiFailure? = null, val requestedPage: Int = 0) {
    val hasNext get() = page?.let { (it.page.toLong() + 1) * it.size < it.total } == true
    val hasPrevious get() = (page?.page ?: 0) > 0
}
data class DiscoveryDetailUiState(val id: UUID? = null, val details: CampaignDetails? = null,
    val loading: Boolean = false, val failure: ApiFailure? = null)
data class CampaignDiscoveryUiState(val ownerId: UUID? = null, val expiresAt: Instant? = null,
    val filters: DiscoveryFilters = DiscoveryFilters(), val appliedFilters: DiscoveryFilters = DiscoveryFilters(),
    val filterFailure: ApiFailure? = null, val results: DiscoveryPageUiState = DiscoveryPageUiState(),
    val opportunities: DiscoveryPageUiState = DiscoveryPageUiState(), val detail: DiscoveryDetailUiState = DiscoveryDetailUiState(),
    val now: Instant = Instant.EPOCH, val navigateToDetail: Boolean = false) {
    val pendingFilters get() = filters.normalized() != appliedFilters
}
