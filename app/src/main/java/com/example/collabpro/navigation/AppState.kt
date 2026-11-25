package com.example.collabpro.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.collabpro.features.campaign.application.BrowseCampaigns
import com.example.collabpro.features.campaign.domain.Campaign
import com.example.collabpro.features.campaign.infrastructure.PreviewCampaigns
import com.example.collabpro.features.collaboration.application.LoadActiveCollaboration
import com.example.collabpro.features.collaboration.infrastructure.PreviewCollaborations
import com.example.collabpro.features.performance.application.LoadResultsPreview
import com.example.collabpro.features.performance.infrastructure.PreviewResults
import com.example.collabpro.features.identity.domain.model.Account
import com.example.collabpro.features.identity.domain.model.AccountType
import java.util.UUID

enum class UserRole { BRAND, CREATOR }

enum class Route {
    WELCOME, ABOUT_BRAND, ABOUT_CREATOR, HOW_BRAND, HOW_CREATOR, CONTACT, ROLE_PICK,
    REGISTER_BRAND, REGISTER_CREATOR, LOGIN, RECOVER,
    BRAND_HOME, CREATOR_HOME, BRAND_PROFILE, CREATOR_PROFILE, SOCIAL_ACCOUNTS,
    CAMPAIGN_SEARCH, CAMPAIGN_DETAIL, APPLICATION_FORM, MY_APPLICATIONS, APPLICATION_DETAIL,
    BRAND_CAMPAIGNS, CAMPAIGN_FORM, CAMPAIGN_TERMS, APPLICANTS, APPLICANT_DETAIL,
    AGREEMENT, COLLABORATIONS, COLLABORATION_DETAIL, EVIDENCE_FORM, REVIEW_DELIVERABLE,
    INCIDENTS, INCIDENT_FORM, PAYMENT_METHODS, PAYMENT_FORM, PLANS, SUBSCRIPTION,
    COMPENSATION, RESULTS, HISTORY, HISTORY_DETAIL
}

class AppState(authenticatedAccount: Account? = null) {
    var authenticatedAccount by mutableStateOf(authenticatedAccount)
        private set
    fun updateAccount(account: Account) {
        if (account.accountId == authenticatedAccount?.accountId && account.accountType == authenticatedAccount?.accountType)
            authenticatedAccount = account
    }
    // These fixtures are loaded only when a future prototype/preview asks for them.
    // Connected routes use their server UiStates and never access these properties.
    val campaigns: List<Campaign> by lazy { BrowseCampaigns(PreviewCampaigns)() }
    val activeCollaboration by lazy { LoadActiveCollaboration(PreviewCollaborations)() }
    val sampleMetrics by lazy { LoadResultsPreview(PreviewResults)() }
    var route by mutableStateOf(if (authenticatedAccount == null) Route.WELCOME else if (authenticatedAccount.accountType == AccountType.BRAND) Route.BRAND_HOME else Route.CREATOR_HOME)
        private set
    var role by mutableStateOf(if (authenticatedAccount?.accountType == AccountType.CREATOR) UserRole.CREATOR else UserRole.BRAND)
        private set
    var campaignId by mutableStateOf(1)
        private set
    var variant by mutableStateOf("")
    var language by mutableStateOf("ES")
    var compensationCollaborationId by mutableStateOf<UUID?>(null)
        private set
    private val backStack = mutableListOf<Pair<Route, UUID?>>()

    fun go(destination: Route) {
        if (authenticatedAccount == null && destination.ordinal >= Route.BRAND_HOME.ordinal) return
        if (authenticatedAccount != null && destination.ordinal < Route.BRAND_HOME.ordinal) return
        if (authenticatedAccount != null && ((destination == Route.BRAND_HOME && role != UserRole.BRAND) ||
            (destination == Route.CREATOR_HOME && role != UserRole.CREATOR))) return
        if (authenticatedAccount != null && ((destination in listOf(Route.CREATOR_PROFILE, Route.SOCIAL_ACCOUNTS) && role != UserRole.CREATOR) ||
            (destination == Route.BRAND_PROFILE && role != UserRole.BRAND))) return
        if (authenticatedAccount != null && destination in listOf(Route.BRAND_CAMPAIGNS, Route.CAMPAIGN_FORM, Route.CAMPAIGN_TERMS, Route.APPLICANTS, Route.APPLICANT_DETAIL, Route.PLANS, Route.SUBSCRIPTION) && role != UserRole.BRAND) return
        if (authenticatedAccount != null && destination in listOf(Route.CAMPAIGN_SEARCH, Route.APPLICATION_FORM, Route.MY_APPLICATIONS, Route.APPLICATION_DETAIL) && role != UserRole.CREATOR) return
        if (route == destination) return
        backStack.add(route to compensationCollaborationId)
        compensationCollaborationId = null
        variant = ""
        route = destination
    }

    fun openCompensation(collaborationId: UUID) {
        if (authenticatedAccount == null) return
        go(Route.COMPENSATION)
        compensationCollaborationId = collaborationId
    }

    fun back() {
        val previous = backStack.removeLastOrNull()
        compensationCollaborationId = previous?.second
        route = previous?.first
            ?: if (authenticatedAccount == null) Route.WELCOME else if (role == UserRole.BRAND) Route.BRAND_HOME else Route.CREATOR_HOME
        variant = ""
    }

    fun selectRole(userRole: UserRole) { if (authenticatedAccount == null) role = userRole }
    fun selectCampaign(id: Int) { campaignId = id }
    fun home() { go(if (role == UserRole.BRAND) Route.BRAND_HOME else Route.CREATOR_HOME) }

    fun snapshot(): List<String> = listOf(route.name, role.name, campaignId.toString(), variant, language, backStack.joinToString(",") { it.first.name })

    fun restore(values: List<String>) {
        if (authenticatedAccount != null) return
        if (values.size < 6) return
        route = runCatching { Route.valueOf(values[0]) }.getOrDefault(Route.WELCOME)
        if (route.ordinal >= Route.BRAND_HOME.ordinal) route = Route.WELCOME
        role = runCatching { UserRole.valueOf(values[1]) }.getOrDefault(UserRole.BRAND)
        campaignId = values[2].toIntOrNull() ?: 1
        variant = values[3]
        language = values[4]
        backStack.clear()
        values[5].split(',').filter { it.isNotBlank() }.forEach { name ->
            runCatching { Route.valueOf(name) }.getOrNull()?.takeIf { it.ordinal < Route.BRAND_HOME.ordinal }?.let { backStack.add(it to null) }
        }
    }

    fun resetToLogin() { backStack.clear(); variant = ""; route = Route.LOGIN }
}
