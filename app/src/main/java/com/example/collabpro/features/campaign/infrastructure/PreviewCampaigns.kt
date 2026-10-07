package com.example.collabpro.features.campaign.infrastructure

import com.example.collabpro.features.campaign.domain.Campaign
import com.example.collabpro.features.campaign.domain.CampaignRepository

/** Local sample content for the UI prototype. No network or persistence. */
object PreviewCampaigns : CampaignRepository {
    private val campaigns = listOf(
        Campaign(1, "Sabores que conectan", "Maki House", "Gastronomía", "Lima • Surco", "Canje + S/ 180", "18 oct 2026", "Abierta", "Video corto y dos historias para presentar nuevos makis al público joven."),
        Campaign(2, "Tu rutina, tu estilo", "Luna Beauty", "Belleza", "Lima", "S/ 350", "25 oct 2026", "Abierta", "Reel de rutina con producto y enlace de atribución."),
        Campaign(3, "Café de barrio", "Café Norte", "Gastronomía", "Lima • Miraflores", "Canje", "12 oct 2026", "Cerrada", "Historias sobre la experiencia en tienda.")
    )
    override fun all(): List<Campaign> = campaigns
}
