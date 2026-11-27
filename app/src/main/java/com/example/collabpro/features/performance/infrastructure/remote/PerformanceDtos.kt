package com.example.collabpro.features.performance.infrastructure.remote

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.performance.domain.model.*
import java.util.UUID
import com.example.collabpro.core.infrastructure.network.required

internal data class PageDto<T>(val items: List<T>?, val total: Long?, val page: Int?, val size: Int?) {
    fun model(request: PageRequest): Page<T> {
        val rows = items.required("items")
        val count = total.required("total")
        require(page == request.page && size == request.size && count >= rows.size && rows.size <= request.size)
        return Page(rows, count, request.page, request.size)
    }
}
internal data class MetricsDto(val collaborationId: UUID?, val automaticAvailability: String?, val availablePublications: Long?,
    val publications: PageDto<Observation>?, val manualEvidenceCount: Long?) {
    fun model(id: UUID, page: PageRequest): MetricsReport {
        require(collaborationId == id)
        return MetricsReport(id, automaticAvailability.required("automaticAvailability"), availablePublications.required("availablePublications"),
            publications.required("publications").model(page), manualEvidenceCount.required("manualEvidenceCount"))
    }
}
internal data class AttributionDto(val collaborationId: UUID?, val availability: String?, val period: Period?,
    val periodEndBoundary: String?, val totals: Counts?, val demonstrationTotals: Counts?,
    val mechanisms: PageDto<MechanismResults>?, val isEstimate: Boolean?, val sources: Map<String, String>?, val warning: String?) {
    fun model(id: UUID, page: PageRequest): AttributionReport {
        require(collaborationId == id && isEstimate == true && periodEndBoundary == "EXCLUSIVE")
        val list = mechanisms.required("mechanisms").model(page)
        require(list.items.all { it.mechanism.collaborationId == id })
        return AttributionReport(id, availability.required("availability"), period.required("period"), periodEndBoundary, totals.required("totals"),
            demonstrationTotals.required("demonstrationTotals"), list, sources.required("sources"), warning.required("warning"))
    }
}
internal data class RefreshDto(val refresh: Refresh?)
internal data class EvidenceDto(val evidence: Evidence?)
internal data class AssetDto(val asset: Asset?)
internal data class MechanismDto(val mechanism: Mechanism?)
internal data class InteractionDto(val interaction: Interaction?)
