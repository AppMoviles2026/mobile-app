package com.example.collabpro.features.performance.infrastructure

import com.example.collabpro.features.performance.domain.*

object PreviewResults : ResultRepository {
    override fun sampleMetrics() = listOf(
        Metric("Alcance", "8 420", "Instagram autorizado", "18–25 oct 2026"),
        Metric("Interacciones", "612", "Instagram autorizado", "18–25 oct 2026"),
        Metric("Visualizaciones", "12 180", "Instagram autorizado", "18–25 oct 2026")
    )
}
