package com.example.collabpro.features.collaboration.presentation.workspace

import com.example.collabpro.core.domain.ApiFailure
import com.example.collabpro.core.domain.Page
import com.example.collabpro.features.collaboration.domain.model.*
import java.time.Instant
import java.util.UUID

internal enum class CollaborationPage {
    HOME, AGREEMENTS, PROPOSAL, AGREEMENT, DETAIL, DELIVERIES, DELIVERY, REVISIONS,
    SUBMIT, CORRECT, INCIDENTS, INCIDENT, OPEN_INCIDENT, PROPOSE_RESOLUTION,
    RESOLUTIONS, HISTORY, HISTORY_DETAIL, TIMELINE
}

internal data class Destination(
    val page: CollaborationPage = CollaborationPage.HOME,
    val collaborationId: UUID? = null,
    val resourceId: UUID? = null,
    val number: Int = 0,
    val filter: String? = null
)

internal sealed interface CollaborationContent {
    data class Collaborations(val page: Page<CollaborationRecord>) : CollaborationContent
    data class Agreements(val page: Page<AgreementRecord>) : CollaborationContent
    data class Agreement(val record: AgreementRecord) : CollaborationContent
    data class Detail(val record: CollaborationRecord) : CollaborationContent
    data class Deliveries(val page: Page<DeliveryRecord>) : CollaborationContent
    data class Delivery(val record: DeliveryRecord) : CollaborationContent
    data class Revisions(val page: Page<DeliveryRevision>) : CollaborationContent
    data class Incidents(val page: Page<IncidentRecord>) : CollaborationContent
    data class Incident(val record: IncidentRecord) : CollaborationContent
    data class Resolutions(val page: Page<ResolutionRecord>) : CollaborationContent
    data class History(val record: HistoryRecord) : CollaborationContent
    data class Timeline(val page: Page<HistoryEntry>) : CollaborationContent
}

internal data class CollaborationUiState(
    val destination: Destination = Destination(),
    val content: CollaborationContent? = null,
    val owner: UUID? = null,
    val expiry: Instant? = null,
    val busy: Boolean = false,
    val failure: ApiFailure? = null,
    val uncertain: Boolean = false,
    val stale: Boolean = false,
    val notice: String? = null,
    val countBack: Int = 0,
    val uploads: List<AssetRecord> = emptyList(),
    val export: AssetRecord? = null,
    val extensionPieces: List<DeliveryRecord> = emptyList()
)
