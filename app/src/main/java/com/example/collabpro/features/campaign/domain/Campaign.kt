package com.example.collabpro.features.campaign.domain

data class Campaign(val id: Int, val title: String, val brand: String, val category: String, val location: String, val compensation: String, val deadline: String, val status: String, val summary: String)
data class CampaignTerms(val requirements: String, val deliverables: String, val date: String, val compensation: String)
data class Application(val creator: String, val audience: String, val niche: String, val message: String, val status: String)

interface CampaignRepository {
    fun all(): List<Campaign>
}
