package com.example.collabpro.features.campaign.domain.model

import java.time.Instant

enum class CampaignAvailability { AVAILABLE, EXPIRED, CLOSED, CANCELLED, NOT_ACCEPTING, UNPUBLISHED }

/** The server flag is authoritative. Local time can only turn availability off, never on. */
fun CampaignSummary.availability(now: Instant): CampaignAvailability = when {
    status == CampaignStatus.DRAFT -> CampaignAvailability.UNPUBLISHED
    status == CampaignStatus.CLOSED -> CampaignAvailability.CLOSED
    status == CampaignStatus.CANCELLED -> CampaignAvailability.CANCELLED
    applicationDeadline != null && !now.isBefore(applicationDeadline) -> CampaignAvailability.EXPIRED
    !acceptsApplications || applicationDeadline == null -> CampaignAvailability.NOT_ACCEPTING
    else -> CampaignAvailability.AVAILABLE
}
