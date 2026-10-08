package com.example.collabpro.auth

import com.example.collabpro.navigation.*
import org.junit.Assert.*
import org.junit.Test

class NavigationAccessTest {
    @Test fun `registration choice is not a private access credential`() {
        val app = AppState()
        app.selectRole(UserRole.CREATOR)
        app.go(Route.CREATOR_HOME)
        assertEquals(Route.WELCOME, app.route)
    }
    @Test fun `authenticated navigation uses server role and ignores local role selectors`() {
        val app = AppState(AuthFixtures.account)
        assertEquals(UserRole.CREATOR, app.role)
        assertEquals(Route.CREATOR_HOME, app.route)
        app.selectRole(UserRole.BRAND)
        assertEquals(UserRole.CREATOR, app.role)
        app.go(Route.BRAND_HOME)
        assertEquals(Route.CREATOR_HOME, app.route)
        app.restore(listOf("BRAND_HOME", "BRAND", "1", "", "ES", "BRAND_PROFILE"))
        assertEquals(UserRole.CREATOR, app.role)
        assertEquals(Route.CREATOR_HOME, app.route)
    }
    @Test fun `saved routes cannot unlock a private screen without authentication`() {
        val app = AppState()
        app.restore(listOf("BRAND_PROFILE", "BRAND", "1", "", "ES", "BRAND_HOME"))
        assertEquals(Route.WELCOME, app.route)
        app.back()
        assertEquals(Route.WELCOME, app.route)
    }
    @Test fun `a new account has no route or selected-resource state from the previous account`() {
        val previous = AppState(AuthFixtures.account)
        previous.go(Route.CAMPAIGN_DETAIL)
        previous.selectCampaign(2)
        previous.variant = "old account data"
        val next = AppState(AuthFixtures.account.copy(accountId = java.util.UUID.randomUUID()))
        assertEquals(Route.CREATOR_HOME, next.route)
        assertEquals(1, next.campaignId)
        assertEquals("", next.variant)
        next.back()
        assertEquals(Route.CREATOR_HOME, next.route)
    }
}
