package com.example.collabpro.campaign

import com.example.collabpro.auth.AuthFixtures
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.drafts.*
import com.example.collabpro.features.campaign.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CampaignValidationTest {
    private fun errors(value: ConditionsDraft) = (CampaignDraftValidation.conditions(value, AuthFixtures.now) as ApiResult.Failure).error.fieldErrors
    @Test fun `all metadata constraints map to input fields`() {
        assertEquals(setOf("title", "objective", "description", "category", "targetAudience", "location"), CampaignDraftValidation.basics(
            CampaignBasics("a".repeat(201), "a".repeat(2001), "a".repeat(5001), "a".repeat(101), "a".repeat(2001), "a".repeat(151)))!!.fieldErrors.keys)
    }
    @Test fun `valid conditions normalize cash and preserve all rules`() {
        val result = CampaignDraftValidation.conditions(CampaignFixtures.terms.copy(currency = " pen ", requirements = listOf(
            RequirementDraft(description = "Manual", expectedValue = "ignored"),
            RequirementDraft(description = "Nicho", rule = RequirementRule.NICHE_EQUALS, expectedValue = " Moda "),
            RequirementDraft(description = "Ubicación", rule = RequirementRule.LOCATION_EQUALS, expectedValue = " Lima "),
            RequirementDraft(description = "Red", rule = RequirementRule.AUTHORIZED_PLATFORM, expectedValue = "TIKTOK"))), AuthFixtures.now) as ApiResult.Success
        assertEquals("PEN", result.value.compensation.currency); assertNull(result.value.requirements[0].expectedValue)
        assertEquals("tiktok", result.value.requirements[3].expectedValue)
    }
    @Test fun `non cash types never send amount or currency`() {
        for (type in CompensationType.entries.filter { it != CompensationType.CASH }) {
            val result = CampaignDraftValidation.conditions(CampaignFixtures.terms.copy(compensationType = type, amount = "999", currency = "USD"), AuthFixtures.now) as ApiResult.Success
            assertNull(result.value.compensation.amount); assertNull(result.value.compensation.currency)
        }
    }
    @Test fun `cash rejects unsupported currency zero exponential and precision overflow`() {
        for (amount in listOf("0", "-1", "1.234", "10000000000", "1e2", "1,20"))
            assertTrue(amount, errors(CampaignFixtures.terms.copy(amount = amount)).containsKey("compensation.amount"))
        assertTrue(errors(CampaignFixtures.terms.copy(currency = "ZZZ")).containsKey("compensation.currency"))
    }
    @Test fun `dates must be future with delivery strictly after application deadline`() {
        assertTrue(errors(CampaignFixtures.terms.copy(applicationDeadline = "2020-02-01 12:00")).containsKey("applicationDeadline"))
        assertTrue(errors(CampaignFixtures.terms.copy(deliverables = listOf(DeliverableDraft(contentType = "Video", description = "Desc", deadline = CampaignFixtures.terms.applicationDeadline)))).containsKey("deliverables[0].deadline"))
    }
    @Test fun `strict dates reject impossible dates and ambiguous daylight saving`() {
        assertNull(CampaignDates.parse("2030-02-30 12:00", "UTC")); assertNull(CampaignDates.parse("2030-02-01", "UTC"))
        assertNull(CampaignDates.parse("2026-11-01 01:30", "America/New_York")); assertNull(CampaignDates.parse("2026-03-08 02:30", "America/New_York"))
    }
    @Test fun `date zone and seconds round trip preserve instant`() {
        val instant = Instant.parse("2030-02-01T12:20:13Z")
        assertEquals(instant, CampaignDates.parse(CampaignDates.display(instant, "America/Lima"), "America/Lima"))
    }
    @Test fun `requirements and deliverables must have valid collection sizes and quantity`() {
        val result = errors(CampaignFixtures.terms.copy(requirements = emptyList(), deliverables = List(51) { DeliverableDraft() }))
        assertTrue(result.containsKey("requirements")); assertTrue(result.containsKey("deliverables")); assertTrue(result.containsKey("deliverables[0].contentType"))
        assertTrue(errors(CampaignFixtures.terms.copy(deliverables = listOf(DeliverableDraft(quantity = "1001")))).containsKey("deliverables[0].quantity"))
    }
    @Test fun `automatic requirements need compatible expected values`() {
        val result = errors(CampaignFixtures.terms.copy(requirements = listOf(RequirementDraft(description = "Red", rule = RequirementRule.AUTHORIZED_PLATFORM, expectedValue = "youtube"))))
        assertTrue(result.containsKey("requirements[0].expectedValue"))
    }
}
