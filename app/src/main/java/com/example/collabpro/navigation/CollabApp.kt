package com.example.collabpro.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.*
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.collabpro.features.billing.presentation.*
import com.example.collabpro.features.campaign.presentation.*
import com.example.collabpro.features.collaboration.presentation.*
import com.example.collabpro.features.identity.presentation.*
import com.example.collabpro.features.identity.presentation.auth.*
import com.example.collabpro.features.identity.application.auth.SessionState
import com.example.collabpro.features.identity.presentation.profile.*
import com.example.collabpro.features.performance.presentation.*

@Composable
fun CollabApp(authentication: AuthenticationViewModel, creatorIdentity: CreatorIdentityViewModel) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) { AuthenticationContent(authentication, creatorIdentity) }
    }
}

@Composable
private fun AuthenticationContent(authentication: AuthenticationViewModel, creatorIdentity: CreatorIdentityViewModel) {
    val session by authentication.session.collectAsStateWithLifecycle()
    val ui by authentication.ui.collectAsStateWithLifecycle()
    val identityUi by creatorIdentity.ui.collectAsStateWithLifecycle()
    val publicApp = rememberSaveable(saver = Saver<AppState, List<String>>(save = { it.snapshot() }, restore = { AppState().apply { restore(it) } })) { AppState() }
    LaunchedEffect(authentication) { authentication.events.collect { publicApp.resetToLogin() } }
    LaunchedEffect(session) {
        if (session is SessionState.SignedOut && (session as SessionState.SignedOut).notice != null) publicApp.resetToLogin()
    }
    LaunchedEffect(session, identityUi.awaitingLogin) {
        if (session is SessionState.SignedOut && identityUi.awaitingLogin) publicApp.resetToLogin()
    }
    if (ui.reset.isOpen) {
        BackHandler { authentication.dismissPasswordReset() }
        ResetPasswordScreen(ui.reset, authentication::newPassword, authentication::passwordConfirmation,
            authentication::resetPassword, authentication::finishReset,
            onRecovery = { authentication.dismissPasswordReset(); authentication.enterForm(AuthForm.RECOVERY); publicApp.go(Route.RECOVER) },
            onBack = authentication::dismissPasswordReset)
        return
    }
    when (val current = session) {
        SessionState.Restoring -> SessionGateScreen()
        is SessionState.VerificationFailed -> SessionGateScreen(current.failure, authentication::restoreSession, authentication::signOut)
        is SessionState.SignedOut -> PublicRoutes(publicApp, ui, identityUi.externalNotice ?: current.notice, authentication)
        is SessionState.Authenticated -> key(current.account.accountId, current.expiresAt) {
            // Not saveable: re-verification and account changes always get a clean private back stack.
            val app = remember { AppState(current.account) }
            LaunchedEffect(current.account) { app.updateAccount(current.account) }
            PrivateRoutes(app, authentication::signOut, creatorIdentity, identityUi)
        }
    }
}

private fun Route.authForm() = when (this) {
    Route.REGISTER_BRAND -> AuthForm.REGISTER_BRAND
    Route.REGISTER_CREATOR -> AuthForm.REGISTER_CREATOR
    Route.LOGIN -> AuthForm.LOGIN
    Route.RECOVER -> AuthForm.RECOVERY
    else -> AuthForm.PUBLIC
}

@Composable
private fun PublicRoutes(app: AppState, ui: AuthenticationUiState, notice: String?, authentication: AuthenticationViewModel) {
    LaunchedEffect(app.route) { authentication.enterForm(app.route.authForm()) }
    fun go(route: Route) { authentication.enterForm(route.authForm()); app.go(route) }
    fun back() { app.back(); authentication.enterForm(app.route.authForm()) }
    BackHandler(enabled = app.route != Route.WELCOME) { back() }
    when (app.route) {
        Route.WELCOME -> WelcomeScreen(app)
        Route.ABOUT_BRAND -> AboutScreen(app, true)
        Route.ABOUT_CREATOR -> AboutScreen(app, false)
        Route.HOW_BRAND -> HowScreen(app, true)
        Route.HOW_CREATOR -> HowScreen(app, false)
        Route.CONTACT -> ContactScreen(app)
        Route.ROLE_PICK -> RolePickerScreen(app)
        Route.REGISTER_BRAND, Route.REGISTER_CREATOR -> RegisterScreen(ui.registration,
            authentication::registrationName, authentication::registrationEmail, authentication::registrationPassword,
            authentication::register, onLogin = { go(Route.LOGIN) }, onBack = ::back)
        Route.LOGIN -> LoginScreen(ui.login.copy(notice = ui.login.notice ?: notice), authentication::loginEmail,
            authentication::loginPassword, authentication::signIn,
            onRecovery = { go(Route.RECOVER) }, onRegister = { go(Route.ROLE_PICK) }, onBack = ::back)
        Route.RECOVER -> RecoverScreen(ui.recovery, authentication::recoveryEmail, authentication::requestRecovery,
            onLogin = { go(Route.LOGIN) }, onBack = ::back)
        else -> WelcomeScreen(app) // A saved/private route cannot bypass verification.
    }
}

@Composable
private fun PrivateRoutes(app: AppState, onSignOut: () -> Unit, creatorIdentity: CreatorIdentityViewModel, identityUi: CreatorIdentityUiState) {
    LaunchedEffect(identityUi.showSocial) {
        if (identityUi.showSocial && app.role == UserRole.CREATOR) { app.go(Route.SOCIAL_ACCOUNTS); creatorIdentity.consumeSocialNavigation() }
    }
    LaunchedEffect(app.route) {
        when (app.route) {
            Route.CREATOR_PROFILE -> if (app.role == UserRole.CREATOR) creatorIdentity.loadProfile()
            Route.SOCIAL_ACCOUNTS -> if (app.role == UserRole.CREATOR) creatorIdentity.loadSocialAccounts()
            else -> Unit
        }
    }
    val home = if (app.role == UserRole.BRAND) Route.BRAND_HOME else Route.CREATOR_HOME
    BackHandler(enabled = app.route != home) { app.back() }
    val tabs = if (app.role == UserRole.BRAND) listOf(
        Triple("Inicio", "⌂", Route.BRAND_HOME), Triple("Campañas", "▣", Route.BRAND_CAMPAIGNS), Triple("Colaborar", "◇", Route.COLLABORATIONS), Triple("Perfil", "○", Route.BRAND_PROFILE)
    ) else listOf(
        Triple("Inicio", "⌂", Route.CREATOR_HOME), Triple("Explorar", "⌕", Route.CAMPAIGN_SEARCH), Triple("Colaborar", "◇", Route.COLLABORATIONS), Triple("Perfil", "○", Route.CREATOR_PROFILE)
    )
    Scaffold(topBar = {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${app.authenticatedAccount?.name} • ${if (app.role == UserRole.BRAND) "Empresa" else "Creador"}",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = onSignOut) { Text("Cerrar sesión") }
            }
            identityUi.externalNotice?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall) }
            if (app.route != home && app.route !in listOf(Route.CREATOR_PROFILE, Route.SOCIAL_ACCOUNTS)) Text("Vista previa • esta función aún no está conectada", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
    }, bottomBar = {
        NavigationBar {
            tabs.forEach { (label, icon, destination) ->
                NavigationBarItem(selected = app.route == destination, onClick = { app.go(destination) }, icon = { Text(icon) }, label = { Text(label) })
            }
        }
    }) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            when (app.route) {
                Route.BRAND_HOME -> HomeScreen(app, true, onSignOut)
                Route.CREATOR_HOME -> HomeScreen(app, false, onSignOut)
                Route.BRAND_PROFILE -> BrandProfileScreen(app)
                Route.CREATOR_PROFILE -> CreatorProfileScreen(identityUi.profile, creatorIdentity::editProfile,
                    creatorIdentity::saveProfile, onReload = { creatorIdentity.loadProfile(force = true) },
                    onDiscard = creatorIdentity::discardChanges, onRefreshAccount = creatorIdentity::refreshAccountName,
                    onSocial = { app.go(Route.SOCIAL_ACCOUNTS) }, onBack = app::back)
                Route.SOCIAL_ACCOUNTS -> LinkedSocialAccountsScreen(identityUi.social, creatorIdentity::startAuthorization,
                    creatorIdentity::loadSocialAccounts, creatorIdentity::checkAuthorization, creatorIdentity::reopenBrowser,
                    creatorIdentity::forgetAttempt, app::back)
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
                else -> HomeScreen(app, app.role == UserRole.BRAND, onSignOut)
            }
        }
    }
}
