package com.example.collabpro.features.performance.application

import com.example.collabpro.features.performance.domain.model.Period
import java.net.URI
import java.time.Instant

object PerformanceInputs {
    private val videoPath = Regex("/@[^/]+/video/([0-9]{1,40})/?")
    private val trackingPath = Regex("/api/v1/public/attribution/[A-Za-z0-9_-]{43}")
    val metrics = listOf("VIEWS", "LIKES", "COMMENTS", "SHARES", "REACH", "IMPRESSIONS", "ENGAGEMENT", "CLICKS", "CONVERSIONS")

    fun videoId(raw: String?): String? {
        val url = https(raw) ?: return null
        if (url.host.lowercase() !in setOf("www.tiktok.com", "tiktok.com") || url.port !in listOf(-1, 443)) return null
        return videoPath.matchEntire(url.path)?.groupValues?.get(1)
    }

    fun https(raw: String?): URI? = uri(raw)?.takeIf { it.scheme.equals("https", ignoreCase = true) }

    fun tracking(raw: String?): URI? = uri(raw)?.takeIf {
        it.scheme in listOf("http", "https") && it.rawQuery == null && it.rawFragment == null && trackingPath.matches(it.rawPath)
    }

    private fun uri(raw: String?) = raw?.trim()?.takeIf { it.length in 1..2048 }
        ?.let { runCatching { URI(it) }.getOrNull() }
        ?.takeIf { it.host != null && it.rawUserInfo == null }

    fun instant(text: String): Instant? = runCatching { Instant.parse(text.trim()) }.getOrNull()

    fun period(start: String, end: String, latest: Instant): Period? {
        val from = instant(start) ?: return null
        val until = instant(end) ?: return null
        return Period(from, until).takeIf { from <= until && until <= latest }
    }
}
