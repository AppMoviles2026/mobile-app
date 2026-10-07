package com.example.collabpro.features.identity.domain

data class BrandProfile(val name: String, val category: String, val location: String, val description: String)
data class CreatorProfile(val name: String, val niche: String, val audience: String, val channels: List<String>)

interface ProfileRepository {
    fun brand(): BrandProfile
    fun creator(): CreatorProfile
}
