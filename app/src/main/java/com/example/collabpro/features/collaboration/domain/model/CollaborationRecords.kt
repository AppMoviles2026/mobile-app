package com.example.collabpro.features.collaboration.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class TermsSnapshot(
    val termsVersion: UUID, val applicationId: UUID, val applicationVersion: Long, val selectedAt: Instant,
    val campaignId: UUID, val campaignVersion: Long, val brandId: UUID, val creatorId: UUID,
    val brandName: String, val creatorName: String, val title: String, val objective: String,
    val description: String?, val category: String, val targetAudience: String, val location: String?,
    val proposal: String, val confirmedRequirements: Set<UUID>, val requirements: List<AgreedRequirement>,
    val deliverables: List<AgreedPiece>, val compensation: AgreedCompensation, val applicationDeadline: Instant,
    val capturedAt: Instant, val acceptanceDeadline: Instant
)
data class AgreedRequirement(val id: UUID, val description: String, val mandatory: Boolean, val ruleType: String, val expectedValue: String?)
data class AgreedPiece(val id: UUID, val contentType: String, val description: String, val quantity: Int, val deadline: Instant)
data class AgreedCompensation(val type: String, val amount: BigDecimal?, val currency: String?, val description: String)
enum class AgreementDecision { ACCEPT, REJECT }
data class AgreementRecord(
    val id: UUID, val snapshot: TermsSnapshot, val status: String,
    val brandAcceptedAt: Instant?, val creatorAcceptedAt: Instant?, val decidedAt: Instant?, val rejectedBy: UUID?,
    val collaborationId: UUID?, val availableActions: List<String>
)
enum class CollaborationStatus { ACTIVE, UNDER_REVIEW, COMPLETED, EXPIRED, CANCELLED, DISPUTED }
data class CollaborationRecord(
    val id: UUID, val agreementId: UUID, val snapshot: TermsSnapshot, val status: CollaborationStatus,
    val startedAt: Instant, val expiredAt: Instant?, val nextDeadline: Instant?, val requiredQuantity: Int,
    val approvedQuantity: Int, val deliverables: List<Obligation>, val pendingActions: List<String>,
    val availableActions: List<String>, val submittedQuantity: Int, val pendingValidationQuantity: Int,
    val rejectedQuantity: Int, val completedAt: Instant?, val cancelledAt: Instant?, val openIncidentCount: Int,
    val canOpenIncident: Boolean, val incidentWindowClosesAt: Instant?
)
data class Obligation(
    val deliverableId: UUID, val specificationId: UUID, val contentType: String, val description: String,
    val requiredQuantity: Int, val approvedQuantity: Int, val deadline: Instant, val overdue: Boolean,
    val submittedQuantity: Int, val pendingValidationQuantity: Int, val rejectedQuantity: Int,
    val correctionDeadline: Instant?, val nextActionDeadline: Instant?
)
data class ContentReference(val url: String? = null, val assetId: UUID? = null)
data class DeliveryBody(val content: ContentReference, val evidence: List<ContentReference>, val notes: String?)
data class Submission(
    val id: UUID, val revision: Int, val submittedAt: Instant, val late: Boolean, val content: DeliveryBody
)
data class Review(val submissionId: UUID, val reviewedAt: Instant, val decision: String, val observations: String?, val correctionDeadline: Instant?)
data class CorrectionExtension(val previousDeadline: Instant, val deadline: Instant, val acceptedAt: Instant)
data class DeliveryRevision(val submission: Submission, val review: Review?, val correctionExtensions: List<CorrectionExtension>)
data class DeliveryRecord(
    val id: UUID, val collaborationId: UUID, val obligationId: UUID, val pieceNumber: Int, val version: Long,
    val status: String, val currentSubmission: Submission, val currentReview: Review?, val availableActions: List<String>,
    val effectiveCorrectionDeadline: Instant?
)
data class AssetRecord(val id: UUID, val collaborationId: UUID, val filename: String, val mediaType: String, val byteSize: Long)
enum class IncidentCategory { CONDITIONS, DELIVERABLE, COMPENSATION, OTHER }
enum class ResolutionOutcome { KEEP_AGREEMENT, EXTEND_CORRECTION, CANCEL_COLLABORATION }
enum class ResolutionDecision { ACCEPT, REJECT, WITHDRAW }
data class ResolutionTerms(
    val outcome: ResolutionOutcome, val description: String, val evidence: List<ContentReference>,
    val deliverableId: UUID? = null, val submissionId: UUID? = null,
    val expectedDeliverableVersion: Long? = null, val correctionDeadline: Instant? = null
)
data class ResolutionRecord(
    val id: UUID, val terms: ResolutionTerms, val proposedBy: UUID, val proposedAt: Instant, val status: String,
    val decidedBy: UUID?, val decidedAt: Instant?, val decisionReason: String?
)
data class IncidentRecord(
    val id: UUID, val collaborationId: UUID, val openedBy: UUID, val category: IncidentCategory, val description: String,
    val deliverableId: UUID?, val evidence: List<ContentReference>, val openedAt: Instant, val status: String,
    val resolvedAt: Instant?, val version: Long, val currentResolution: ResolutionRecord?, val availableActions: List<String>
)
data class HistoryEntry(
    val id: UUID, val collaborationId: UUID, val sequence: Long, val type: String, val actorAccountId: UUID?,
    val occurredAt: Instant, val reconstructed: Boolean, val details: Map<String, Any?>
)
data class ResourceReference(val collaborationId: UUID, val availability: String, val resourceId: UUID?)
data class HistoryReferences(val compensation: ResourceReference, val performance: ResourceReference)
data class HistoryRecord(val collaboration: CollaborationRecord, val agreement: AgreementRecord, val references: HistoryReferences)
