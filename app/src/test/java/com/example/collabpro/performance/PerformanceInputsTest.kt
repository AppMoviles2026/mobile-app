package com.example.collabpro.performance

import com.example.collabpro.features.performance.application.PerformanceInputs
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PerformanceInputsTest {
    @Test fun `only a direct TikTok link supplies a real post identifier`() {
        assertEquals("7462397054809525550",
            PerformanceInputs.videoId("https://www.tiktok.com/@creator/video/7462397054809525550?lang=es"))
        assertNull(PerformanceInputs.videoId("https://vm.tiktok.com/Zfoo"))
        assertNull(PerformanceInputs.videoId("https://www.tiktok.com.example.test/@creator/video/123"))
        assertNull(PerformanceInputs.videoId("https://user@tiktok.com/@creator/video/123"))
        assertNull(PerformanceInputs.videoId("https://tiktok.com/@creator/video/fake"))
    }

    @Test fun `public share uses only the server tracking URL shape`() {
        val url = "https://backend.example.test/api/v1/public/attribution/" + "t".repeat(43)
        assertEquals(url, PerformanceInputs.tracking(url).toString())
        assertNull(PerformanceInputs.tracking("$url?redirect=https://example.test"))
        assertNull(PerformanceInputs.tracking("$url#fragment"))
        assertNull(PerformanceInputs.tracking(url.replace("/public/", "/private/")))
    }

    @Test fun `dates accept local entry in the device time zone`() {
        val lima = ZoneId.of("America/Lima")
        assertEquals(Instant.parse("2026-11-27T21:34:00Z"), PerformanceInputs.instant("27/11/2026 16:34", lima))
        assertEquals(Instant.parse("2026-11-27T21:34:15Z"), PerformanceInputs.instant("27/11/2026 16:34:15", lima))
        assertNull(PerformanceInputs.instant("31/02/2026 16:34", lima))
    }

    @Test fun `period must have both bounds ordered and no later than observation`() {
        val now = Instant.parse("2026-11-27T16:00:00Z")
        val from = now.minusSeconds(100)
        assertEquals(from, PerformanceInputs.period(from.toString(), now.toString(), now)?.start)
        assertNull(PerformanceInputs.period(from.toString(), "", now))
        assertNull(PerformanceInputs.period(now.toString(), from.toString(), now))
        assertNull(PerformanceInputs.period(from.toString(), now.plusSeconds(1).toString(), now))
    }
}
