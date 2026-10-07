package com.example.collabpro.features.identity.infrastructure

import com.example.collabpro.features.identity.domain.*

object PreviewProfiles : ProfileRepository {
    override fun brand() = BrandProfile("Maki House", "Gastronomía", "Lima, Perú", "Dark kitchen de makis para público joven.")
    override fun creator() = CreatorProfile("Camila Rojas", "Food & lifestyle", "Jóvenes de 18 a 30 años", listOf("Instagram", "TikTok"))
}
