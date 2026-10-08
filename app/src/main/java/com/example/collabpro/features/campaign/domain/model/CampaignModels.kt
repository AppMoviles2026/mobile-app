package com.example.collabpro.features.campaign.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class CampaignStatus { DRAFT, OPEN, CLOSED, CANCELLED }
enum class CompensationType { CASH, PRODUCT, SERVICE, CREDIT, BARTER }
data class Compensation(val type: CompensationType, val amount: BigDecimal?, val currency: String?, val description: String)
enum class RequirementRule { MANUAL_CONFIRMATION, NICHE_EQUALS, LOCATION_EQUALS, AUTHORIZED_PLATFORM }
data class Requirement(val id: UUID, val description: String, val mandatory: Boolean, val ruleType: RequirementRule, val expectedValue: String?)
data class DeliverableSpec(val id: UUID, val contentType: String, val description: String, val quantity: Int, val deadline: Instant)
data class CampaignSummary(
    val id: UUID, val brandId: UUID, val brandName: String, val title: String,
    val category: String, val location: String?, val compensation: Compensation?,
    val applicationDeadline: Instant?, val status: CampaignStatus, val acceptsApplications: Boolean
)
data class CampaignDetails(
    val summary: CampaignSummary, val objective: String, val description: String?, val targetAudience: String,
    val publicationDate: Instant?, val requirements: List<Requirement>, val deliverables: List<DeliverableSpec>
)
enum class ApplicationStatus { PENDING, SELECTED, REJECTED, CANCELLED }
data class Application(
    val id: UUID, val campaignId: UUID, val creatorId: UUID, val campaignTitle: String, val brandName: String,
    val message: String, val status: ApplicationStatus, val submittedAt: Instant,
    val confirmedRequirementIds: Set<UUID>, val version: Long
)
data class NewCampaign(val title: String, val objective: String, val description: String?, val category: String, val targetAudience: String, val location: String?)
data class NewRequirement(val description: String, val mandatory: Boolean, val ruleType: RequirementRule, val expectedValue: String?)
data class NewDeliverable(val contentType: String, val description: String, val quantity: Int, val deadline: Instant)
data class CampaignConditions(
    val requirements: List<NewRequirement>, val deliverables: List<NewDeliverable>,
    val applicationDeadline: Instant, val compensation: Compensation
)
data class CampaignSearch(val query: String? = null, val category: String? = null, val location: String? = null, val compensationType: CompensationType? = null)

/** Generate ONCE per user intent and reuse on retry after timeout; never one key per HTTP attempt. */
data class IdempotencyKey(val value: UUID = UUID.randomUUID())
