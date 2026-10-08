package com.example.collabpro.campaign

import com.example.collabpro.auth.AuthFixtures
import com.example.collabpro.features.campaign.application.discovery.DiscoveryFilters
import com.example.collabpro.features.campaign.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class CampaignDiscoveryRulesTest {
    private val now = AuthFixtures.now
    private val summary = discoveryCampaign().summary
    @Test fun `open future campaign is available only when server accepts applications`() {
        assertEquals(CampaignAvailability.AVAILABLE, summary.availability(now))
        assertEquals(CampaignAvailability.NOT_ACCEPTING, summary.copy(acceptsApplications = false).availability(now))
    }
    @Test fun `exact deadline and later are expired`() {
        assertEquals(CampaignAvailability.EXPIRED, summary.availability(summary.applicationDeadline!!))
        assertEquals(CampaignAvailability.EXPIRED, summary.availability(summary.applicationDeadline!!.plusSeconds(1)))
    }
    @Test fun `closed cancelled and draft never become available even with true server flag`() {
        assertEquals(CampaignAvailability.CLOSED, summary.copy(status = CampaignStatus.CLOSED).availability(now))
        assertEquals(CampaignAvailability.CANCELLED, summary.copy(status = CampaignStatus.CANCELLED).availability(now))
        assertEquals(CampaignAvailability.UNPUBLISHED, summary.copy(status = CampaignStatus.DRAFT).availability(now))
    }
    @Test fun `missing deadline cannot produce false availability`() { assertEquals(CampaignAvailability.NOT_ACCEPTING, summary.copy(applicationDeadline = null).availability(now)) }
    @Test fun `local clock cannot override negative server availability`() {
        assertEquals(CampaignAvailability.NOT_ACCEPTING, summary.copy(acceptsApplications = false).availability(now.minusSeconds(999999)))
    }
    @Test fun `blank inputs become absent query parameters`() {
        val filters = DiscoveryFilters("  ", "\t", "\n")
        assertTrue(filters.isEmpty); assertEquals(CampaignSearch(), filters.search()); assertNull(filters.validation())
    }
    @Test fun `all five compensation types have actual backend values`() {
        for (type in CompensationType.entries) assertEquals(type, DiscoveryFilters(compensationType = type).search().compensationType)
    }
    @Test fun `filter limits match backend and normalization retains internal text`() {
        assertNull(DiscoveryFilters(" x ".repeat(66), "x".repeat(100), "x".repeat(150)).validation())
        assertNotNull(DiscoveryFilters(query = "x".repeat(201)).validation())
        assertEquals("Maki  House", DiscoveryFilters(query = " Maki  House ").search().query)
    }
}
