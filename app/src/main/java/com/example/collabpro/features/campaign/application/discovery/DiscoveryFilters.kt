package com.example.collabpro.features.campaign.application.discovery

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.domain.model.*

data class DiscoveryFilters(val query: String = "", val category: String = "", val location: String = "",
    val compensationType: CompensationType? = null) {
    fun normalized() = copy(query = query.trim(), category = category.trim(), location = location.trim())
    val isEmpty get() = query.isBlank() && category.isBlank() && location.isBlank() && compensationType == null
    fun search() = CampaignSearch(query.trim().ifEmpty { null }, category.trim().ifEmpty { null },
        location.trim().ifEmpty { null }, compensationType)
    fun validation(): ApiFailure? {
        val value = normalized()
        val errors = buildMap {
            if (value.query.length > 200) put("q", "Usa como máximo 200 caracteres.")
            if (value.category.length > 100) put("category", "Usa como máximo 100 caracteres.")
            if (value.location.length > 150) put("location", "Usa como máximo 150 caracteres.")
        }
        return if (errors.isEmpty()) null else ApiFailure(FailureKind.VALIDATION,
            message = "Revisa los filtros antes de buscar.", fieldErrors = errors)
    }
}
