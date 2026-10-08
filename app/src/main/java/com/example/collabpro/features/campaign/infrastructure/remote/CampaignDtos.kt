package com.example.collabpro.features.campaign.infrastructure.remote

import java.math.BigDecimal
import java.time.Instant

internal data class PageDto<T>(val items: List<T>?, val total: Long?, val page: Int?, val size: Int?)
internal data class CompensationDto(val type: String?, val amount: BigDecimal?, val currency: String?, val description: String?)
/** Details are FLAT in the API; summary is a domain grouping only. */
internal data class CampaignDto(
    val id: String?, val brandId: String?, val brandName: String?, val title: String?, val category: String?,
    val location: String?, val compensation: CompensationDto?, val applicationDeadline: Instant?, val status: String?,
    val acceptsApplications: Boolean?, val objective: String?, val description: String?, val targetAudience: String?,
    val publicationDate: Instant?, val requirements: List<RequirementDto>?, val deliverables: List<DeliverableDto>?
)
internal data class RequirementDto(val id: String?, val description: String?, val mandatory: Boolean?, val ruleType: String?, val expectedValue: String?)
internal data class DeliverableDto(val id: String?, val contentType: String?, val description: String?, val quantity: Int?, val deadline: Instant?)
internal data class ApplicationDto(
    val id: String?, val campaignId: String?, val creatorId: String?, val campaignTitle: String?, val brandName: String?,
    val message: String?, val status: String?, val submittedAt: Instant?, val confirmedRequirementIds: Set<String>?, val version: Long?
)
internal data class CreateCampaignDto(val title: String, val objective: String, val description: String?, val category: String, val targetAudience: String, val location: String?)
internal data class NewRequirementDto(val description: String, val mandatory: Boolean, val ruleType: String, val expectedValue: String?)
internal data class NewDeliverableDto(val contentType: String, val description: String, val quantity: Int, val deadline: Instant)
internal data class ConditionsDto(val requirements: List<NewRequirementDto>, val deliverables: List<NewDeliverableDto>, val applicationDeadline: Instant, val compensation: CompensationDto)
internal data class SubmitApplicationDto(val message: String, val confirmedRequirementIds: Set<String>)
internal data class UpdateApplicationDto(val message: String, val expectedVersion: Long)
internal data class CancelApplicationDto(val expectedVersion: Long)
