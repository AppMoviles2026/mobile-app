package com.example.collabpro.features.identity.domain.model

import java.util.UUID

data class BusinessProfile(
    val profileId: UUID,
    val businessName: String,
    val description: String?,
    val category: String?,
    val location: String?,
    val version: Long
)

data class BusinessProfileUpdate(
    val businessName: String,
    val description: String?,
    val category: String?,
    val location: String?,
    val expectedVersion: Long
) {
    fun normalized() = copy(
        businessName = businessName.trim(),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        category = category?.trim()?.takeIf { it.isNotEmpty() },
        location = location?.trim()?.takeIf { it.isNotEmpty() }
    )
}
