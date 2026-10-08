package com.example.collabpro.navigation

import java.util.UUID

/** Real-resource arguments for the next UI integration phase. Never carry tokens or prototype Int IDs. */
sealed interface BackendDestination {
    data object Authentication : BackendDestination
    data object CreatorProfile : BackendDestination
    data object CampaignSearch : BackendDestination
    data object OwnCampaigns : BackendDestination
    data object OwnApplications : BackendDestination
    data class CampaignDetails(val campaignId: UUID) : BackendDestination
    data class CampaignConditions(val campaignId: UUID) : BackendDestination
    data class ApplicationDetails(val applicationId: UUID) : BackendDestination
}
