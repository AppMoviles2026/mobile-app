package com.example.collabpro.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.example.collabpro.features.billing.presentation.*
import com.example.collabpro.features.campaign.presentation.*
import com.example.collabpro.features.collaboration.presentation.*
import com.example.collabpro.features.identity.presentation.*
import com.example.collabpro.features.performance.presentation.*

@Composable
fun CollabApp() {
    val app = rememberSaveable(saver = Saver<AppState, List<String>>(save = { it.snapshot() }, restore = { AppState().apply { restore(it) } })) { AppState() }
    BackHandler(enabled = app.route != Route.WELCOME) { app.back() }
    val tabs = if (app.role == UserRole.BRAND) listOf(
        Triple("Inicio", "⌂", Route.BRAND_HOME), Triple("Campañas", "▣", Route.BRAND_CAMPAIGNS), Triple("Colaborar", "◇", Route.COLLABORATIONS), Triple("Perfil", "○", Route.BRAND_PROFILE)
    ) else listOf(
        Triple("Inicio", "⌂", Route.CREATOR_HOME), Triple("Explorar", "⌕", Route.CAMPAIGN_SEARCH), Triple("Colaborar", "◇", Route.COLLABORATIONS), Triple("Perfil", "○", Route.CREATOR_PROFILE)
    )
    val inApp = app.route.ordinal >= Route.BRAND_HOME.ordinal
    Scaffold(bottomBar = {
        if (inApp) NavigationBar {
            tabs.forEach { (label, icon, destination) ->
                NavigationBarItem(selected = app.route == destination, onClick = { app.go(destination) }, icon = { Text(icon) }, label = { Text(label) })
            }
        }
    }) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            when (app.route) {
                Route.WELCOME -> WelcomeScreen(app)
                Route.ABOUT_BRAND -> AboutScreen(app, true)
                Route.ABOUT_CREATOR -> AboutScreen(app, false)
                Route.HOW_BRAND -> HowScreen(app, true)
                Route.HOW_CREATOR -> HowScreen(app, false)
                Route.CONTACT -> ContactScreen(app)
                Route.ROLE_PICK -> RolePickerScreen(app)
                Route.REGISTER_BRAND -> RegisterScreen(app, true)
                Route.REGISTER_CREATOR -> RegisterScreen(app, false)
                Route.LOGIN -> LoginScreen(app)
                Route.RECOVER -> RecoverScreen(app)
                Route.BRAND_HOME -> HomeScreen(app, true)
                Route.CREATOR_HOME -> HomeScreen(app, false)
                Route.BRAND_PROFILE -> ProfileScreen(app, true)
                Route.CREATOR_PROFILE -> ProfileScreen(app, false)
                Route.SOCIAL_ACCOUNTS -> SocialAccountsScreen(app)
                Route.CAMPAIGN_SEARCH -> CampaignSearchScreen(app)
                Route.CAMPAIGN_DETAIL -> CampaignDetailScreen(app)
                Route.APPLICATION_FORM -> ApplicationFormScreen(app)
                Route.MY_APPLICATIONS -> MyApplicationsScreen(app)
                Route.BRAND_CAMPAIGNS -> BrandCampaignsScreen(app)
                Route.CAMPAIGN_FORM -> CampaignFormScreen(app)
                Route.CAMPAIGN_TERMS -> CampaignTermsScreen(app)
                Route.APPLICANTS -> ApplicantsScreen(app)
                Route.APPLICANT_DETAIL -> ApplicantDetailScreen(app)
                Route.AGREEMENT -> AgreementScreen(app)
                Route.COLLABORATIONS -> CollaborationsScreen(app)
                Route.COLLABORATION_DETAIL -> CollaborationDetailScreen(app)
                Route.EVIDENCE_FORM -> EvidenceFormScreen(app)
                Route.REVIEW_DELIVERABLE -> ReviewDeliverableScreen(app)
                Route.INCIDENTS -> IncidentsScreen(app)
                Route.INCIDENT_FORM -> IncidentFormScreen(app)
                Route.PAYMENT_METHODS -> PaymentMethodsScreen(app)
                Route.PAYMENT_FORM -> PaymentFormScreen(app)
                Route.PLANS -> PlansScreen(app)
                Route.SUBSCRIPTION -> SubscriptionScreen(app)
                Route.COMPENSATION -> CompensationScreen(app)
                Route.RESULTS -> ResultsScreen(app)
                Route.HISTORY -> HistoryScreen(app)
                Route.HISTORY_DETAIL -> HistoryDetailScreen(app)
            }
        }
    }
}
