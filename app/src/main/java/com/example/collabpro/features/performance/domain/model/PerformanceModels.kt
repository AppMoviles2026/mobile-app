package com.example.collabpro.features.performance.domain.model

import com.example.collabpro.core.domain.Page
import java.time.Instant
import java.util.UUID

data class Period(val start: Instant, val end: Instant)
data class Publication(val id: UUID, val socialId: UUID, val platform: String, val postId: String)
data class Binding(val submissionId: UUID, val publicationUrl: String)
data class Snapshot(val id: UUID, val submissionId: UUID, val sourceType: String, val provider: String,
    val periodKind: String, val capturedAt: Instant, val period: Period, val metrics: Map<String, Long>)
data class Refresh(val status: String, val checkedAt: Instant)
data class Observation(val publication: Publication, val bindings: List<Binding>, val appliesToCurrentContent: Boolean,
    val availability: String, val snapshot: Snapshot?, val lastRefresh: Refresh?)
data class MetricsReport(val collaborationId: UUID, val automaticAvailability: String, val availablePublications: Long,
    val publications: Page<Observation>, val manualEvidenceCount: Long)
data class Reference(val url: String? = null, val assetId: UUID? = null)
data class Evidence(val id: UUID, val collaborationId: UUID, val sourceType: String, val submissionId: UUID?,
    val description: String, val references: List<Reference>, val claimedMetrics: Map<String, Long>,
    val observedAt: Instant, val period: Period, val submittedAt: Instant, val status: String,
    val demonstration: Boolean, val version: Long, val reviewedAt: Instant?, val observations: String?)
data class Asset(val id: UUID, val filename: String, val mediaType: String, val byteSize: Long, val sha256: String)
data class Document(val name: String, val mediaType: String, val bytes: ByteArray)

data class Destination(val id: String, val url: String)
enum class MechanismKind { LINK, CODE }
enum class MechanismStatus { ACTIVE, INACTIVE }
enum class InteractionType { INQUIRY, CODE_REDEMPTION }
enum class EvidenceDecision { VERIFIED, REJECTED }
data class Mechanism(val id: UUID, val collaborationId: UUID, val kind: MechanismKind, val label: String,
    val trackingUrl: String?, val code: String?, val status: MechanismStatus, val availability: String,
    val demonstration: Boolean, val createdAt: Instant, val version: Long)
data class Counts(val clicks: Long, val inquiries: Long, val codeRedemptions: Long)
data class MechanismResults(val mechanism: Mechanism, val interactions: Counts)
data class AttributionReport(val collaborationId: UUID, val availability: String, val period: Period,
    val periodEndBoundary: String, val totals: Counts, val demonstrationTotals: Counts, val mechanisms: Page<MechanismResults>,
    val sources: Map<String, String>, val warning: String)
data class Interaction(val id: UUID, val attributionId: UUID, val type: String, val source: String,
    val occurredAt: Instant, val recordedAt: Instant, val evidenceId: UUID?, val evidenceStatus: String?,
    val includedInTotals: Boolean, val demonstration: Boolean)

data class RefreshMetrics(val requestId: UUID, val socialId: UUID, val submissionId: UUID, val postId: String)
data class RegisterEvidence(val requestId: UUID, val submissionId: UUID?, val description: String,
    val references: List<Reference>, val claimedMetrics: Map<String, Long>, val observedAt: Instant, val period: Period)
data class ReviewEvidence(val requestId: UUID, val expectedVersion: Long, val decision: EvidenceDecision, val observations: String?)
data class CreateMechanism(val requestId: UUID, val kind: MechanismKind, val label: String, val destinationId: String?)
data class ChangeMechanism(val requestId: UUID, val expectedVersion: Long, val status: MechanismStatus)
data class RecordInteraction(val requestId: UUID, val eventId: UUID, val type: InteractionType,
    val occurredAt: Instant, val evidenceId: UUID)
