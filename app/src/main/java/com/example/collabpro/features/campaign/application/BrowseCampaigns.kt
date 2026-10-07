package com.example.collabpro.features.campaign.application

import com.example.collabpro.features.campaign.domain.Campaign
import com.example.collabpro.features.campaign.domain.CampaignRepository

class BrowseCampaigns(private val repository: CampaignRepository) {
    operator fun invoke(): List<Campaign> = repository.all()
}
