package com.example.collabpro.features.campaign.infrastructure.remote

import com.example.collabpro.core.domain.Page
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.campaign.domain.model.*

internal fun CompensationDto.toDomain() = Compensation(type.enum("type"), amount, currency, description.required("description"))
internal fun Compensation.toDto() = CompensationDto(type.name, amount, currency, description)
internal fun CampaignDto.toSummary() = CampaignSummary(id.uuid("id"), brandId.uuid("brandId"), brandName.required("brandName"),
    title.required("title"), category.required("category"), location, compensation?.toDomain(), applicationDeadline,
    status.enum("status"), acceptsApplications.required("acceptsApplications"))
internal fun CampaignDto.toDetails() = CampaignDetails(toSummary(), objective.required("objective"), description,
    targetAudience.required("targetAudience"), publicationDate,
    requirements.required("requirements").map { item -> Requirement(item.id.uuid("id"), item.description.required("description"),
        item.mandatory.required("mandatory"), item.ruleType.enum("ruleType"), item.expectedValue) },
    deliverables.required("deliverables").map { item -> DeliverableSpec(item.id.uuid("id"), item.contentType.required("contentType"),
        item.description.required("description"), item.quantity.required("quantity"), item.deadline.required("deadline")) })
internal fun ApplicationDto.toDomain(): Application {
    val version = version.required("version")
    if (version < 0) throw InvalidApiResponse("Invalid application version")
    return Application(id.uuid("id"), campaignId.uuid("campaignId"), creatorId.uuid("creatorId"),
        campaignTitle.required("campaignTitle"), brandName.required("brandName"), message.required("message"),
        status.enum("status"), submittedAt.required("submittedAt"),
        confirmedRequirementIds.required("confirmedRequirementIds").map { it.uuid("confirmedRequirementId") }.toSet(), version)
}
internal fun <D, T> PageDto<D>.toDomain(map: (D) -> T): Page<T> {
    val page = page.required("page")
    val size = size.required("size")
    val total = total.required("total")
    val items = items.required("items")
    if (page < 0 || size !in 1..100 || total < 0 || items.size > size || items.size > total) throw InvalidApiResponse("Invalid pagination")
    return Page(items.map(map), total, page, size)
}
internal fun NewCampaign.toDto() = CreateCampaignDto(title, objective, description, category, targetAudience, location)
internal fun CampaignConditions.toDto() = ConditionsDto(
    requirements.map { NewRequirementDto(it.description, it.mandatory, it.ruleType.name, it.expectedValue) },
    deliverables.map { NewDeliverableDto(it.contentType, it.description, it.quantity, it.deadline) }, applicationDeadline, compensation.toDto())
