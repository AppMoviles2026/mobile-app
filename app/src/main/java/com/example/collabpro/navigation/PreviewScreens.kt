package com.example.collabpro.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.collabpro.features.billing.presentation.*
import com.example.collabpro.features.campaign.presentation.*
import com.example.collabpro.features.collaboration.presentation.*
import com.example.collabpro.features.identity.presentation.*
import com.example.collabpro.features.identity.presentation.auth.*
import com.example.collabpro.features.identity.domain.model.AccountType
import com.example.collabpro.features.identity.domain.model.*
import com.example.collabpro.features.identity.presentation.profile.*
import com.example.collabpro.core.domain.*
import java.util.UUID
import com.example.collabpro.features.campaign.presentation.manage.*
import com.example.collabpro.features.campaign.presentation.discovery.*
import com.example.collabpro.features.campaign.presentation.applications.*
import com.example.collabpro.features.campaign.application.applications.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.application.discovery.DiscoveryFilters
import java.time.Instant
import com.example.collabpro.features.campaign.application.drafts.*
import com.example.collabpro.features.performance.presentation.*
import com.example.collabpro.ui.theme.CollabProTheme

// Previews for all existing screens/routes. These wrappers add no app functionality.

@Preview(name = "Inicio público", showBackground = true, showSystemUi = true)
@Composable private fun WelcomePreview() = preview { WelcomeScreen(AppState()) }

@Preview(name = "Acerca de • Empresa", showBackground = true, showSystemUi = true)
@Composable private fun AboutBrandPreview() = preview { AboutScreen(AppState(), true) }

@Preview(name = "Acerca de • Creador", showBackground = true, showSystemUi = true)
@Composable private fun AboutCreatorPreview() = preview { AboutScreen(AppState(), false) }

@Preview(name = "Cómo funciona • Empresa", showBackground = true, showSystemUi = true)
@Composable private fun HowBrandPreview() = preview { HowScreen(AppState(), true) }

@Preview(name = "Cómo funciona • Creador", showBackground = true, showSystemUi = true)
@Composable private fun HowCreatorPreview() = preview { HowScreen(AppState(), false) }

@Preview(name = "Contacto", showBackground = true, showSystemUi = true)
@Composable private fun ContactPreview() = preview { ContactScreen(AppState()) }

@Preview(name = "Elegir perfil", showBackground = true, showSystemUi = true)
@Composable private fun RolePickerPreview() = preview { RolePickerScreen(AppState()) }

@Preview(name = "Registro • Empresa", showBackground = true, showSystemUi = true)
@Composable private fun RegisterBrandPreview() = preview { RegisterScreen(RegistrationUiState(type = AccountType.BRAND)) }

@Preview(name = "Registro • Creador", showBackground = true, showSystemUi = true)
@Composable private fun RegisterCreatorPreview() = preview { RegisterScreen(RegistrationUiState(type = AccountType.CREATOR)) }

@Preview(name = "Inicio de sesión", showBackground = true, showSystemUi = true)
@Composable private fun LoginPreview() = preview { LoginScreen(LoginUiState()) }

@Preview(name = "Recuperar acceso", showBackground = true, showSystemUi = true)
@Composable private fun RecoverPreview() = preview { RecoverScreen(RecoveryUiState()) }

@Preview(name = "Nueva contraseña", showBackground = true, showSystemUi = true)
@Composable private fun ResetPasswordPreview() = preview { ResetPasswordScreen(ResetPasswordUiState(linkValid = true)) }

@Preview(name = "Contraseña restablecida", showBackground = true, showSystemUi = true)
@Composable private fun ResetCompletedPreview() = preview { ResetPasswordScreen(ResetPasswordUiState(completed = true)) }

@Preview(name = "Verificando sesión", showBackground = true, showSystemUi = true)
@Composable private fun SessionGatePreview() = preview { SessionGateScreen() }

@Preview(name = "Panel de empresa", showBackground = true, showSystemUi = true)
@Composable private fun BrandHomePreview() = preview {
    HomeScreen(AppState().apply { selectRole(UserRole.BRAND) }, true)
}

@Preview(name = "Panel de creador", showBackground = true, showSystemUi = true)
@Composable private fun CreatorHomePreview() = preview {
    HomeScreen(AppState().apply { selectRole(UserRole.CREATOR) }, false)
}

@Preview(name = "Perfil • Empresa", showBackground = true, showSystemUi = true)
@Composable private fun BrandProfilePreview() = preview { BrandProfileScreen(AppState()) }

@Preview(name = "Perfil • Creador", showBackground = true, showSystemUi = true)
@Composable private fun CreatorProfilePreview() = preview {
    val profile = CreatorProfile(UUID.fromString("00000000-0000-0000-0000-000000000013"), "Camila Rojas",
        "Contenido gastronómico y experiencias locales.", "Gastronomía", "Jóvenes de 18 a 30 años", "Lima")
    CreatorProfileScreen(CreatorProfileUiState(profile, ProfileDraft.from(profile)))
}

@Preview(name = "Cuentas sociales", showBackground = true, showSystemUi = true)
@Composable private fun SocialAccountsPreview() = preview { LinkedSocialAccountsScreen(SocialAccountsUiState(loaded = true)) }

@Preview(name = "Perfil • Error de carga", showBackground = true, showSystemUi = true)
@Composable private fun CreatorProfileErrorPreview() = preview {
    CreatorProfileScreen(CreatorProfileUiState(failure = ApiFailure(FailureKind.NETWORK, message = "No se pudo conectar con el servidor.")))
}

@Preview(name = "Redes • Autorizadas", showBackground = true, showSystemUi = true)
@Composable private fun LinkedAccountsPreview() = preview {
    LinkedSocialAccountsScreen(SocialAccountsUiState(loaded = true, accounts = listOf(
        SocialAccount(UUID.fromString("00000000-0000-0000-0000-000000000014"), SocialPlatform.INSTAGRAM, "camila.crea", SocialAccountStatus.ACTIVE))))
}

@Preview(name = "Redes • Permiso rechazado", showBackground = true, showSystemUi = true)
@Composable private fun SocialDeniedPreview() = preview {
    LinkedSocialAccountsScreen(SocialAccountsUiState(loaded = true, link = SocialLinkUiState(
        UUID.fromString("00000000-0000-0000-0000-000000000015"), SocialPlatform.TIKTOK, AuthorizationStatus.FAILED,
        message = "No concediste los permisos necesarios. No se vinculó una cuenta nueva.")))
}

@Preview(name = "Redes • Verificando retorno", showBackground = true, showSystemUi = true)
@Composable private fun SocialCheckingPreview() = preview {
    LinkedSocialAccountsScreen(SocialAccountsUiState(link = SocialLinkUiState(
        UUID.fromString("00000000-0000-0000-0000-000000000016"), SocialPlatform.INSTAGRAM, checking = true)))
}

@Preview(name = "Buscar campañas", showBackground = true, showSystemUi = true)
@Composable private fun CampaignSearchPreview() = preview { CampaignExploreScreen(CampaignDiscoveryUiState(
    results = DiscoveryPageUiState(page = Page(listOf(previewCampaign.summary), 1, 0, 20)), now = Instant.parse("2030-01-01T00:00:00Z"))) }

@Preview(name = "Detalle de campaña", showBackground = true, showSystemUi = true)
@Composable private fun CampaignDetailPreview() = preview { CreatorCampaignDetailScreen(DiscoveryDetailUiState(
    previewCampaign.summary.id, previewCampaign), Instant.parse("2030-01-01T00:00:00Z")) }

@Preview(name = "Explorar • Sin coincidencias", showBackground = true, showSystemUi = true)
@Composable private fun CampaignEmptyPreview() = preview { CampaignExploreScreen(CampaignDiscoveryUiState(
    filters = DiscoveryFilters(category = "Moda"), appliedFilters = DiscoveryFilters(category = "Moda"),
    results = DiscoveryPageUiState(page = Page(emptyList(), 0, 0, 20)))) }

@Preview(name = "Explorar • Error de conexión", showBackground = true, showSystemUi = true)
@Composable private fun CampaignSearchErrorPreview() = preview { CampaignExploreScreen(CampaignDiscoveryUiState(
    results = DiscoveryPageUiState(failure = ApiFailure(FailureKind.NETWORK, message = "No se pudo conectar con el servidor.")))) }

@Preview(name = "Detalle • No admite postulaciones", showBackground = true, showSystemUi = true)
@Composable private fun ClosedCampaignPreview() = preview { CreatorCampaignDetailScreen(DiscoveryDetailUiState(
    previewCampaign.summary.id, previewCampaign.copy(summary = previewCampaign.summary.copy(status = CampaignStatus.CLOSED, acceptsApplications = false))),
    Instant.parse("2030-01-01T00:00:00Z")) }

private val previewCampaign = CampaignDetails(CampaignSummary(UUID.fromString("00000000-0000-0000-0000-000000000017"),
    UUID.fromString("00000000-0000-0000-0000-000000000018"), "Empresa de ejemplo", "Experiencias locales", "Gastronomía", "Lima",
    Compensation(CompensationType.PRODUCT, null, null, "Degustación por contenido"), Instant.parse("2030-02-01T18:00:00Z"), CampaignStatus.OPEN, true),
    "Presentar una experiencia gastronómica", "Contenido auténtico de una visita al local.", "Jóvenes interesados en gastronomía",
    Instant.parse("2029-12-30T10:00:00Z"), listOf(Requirement(UUID.fromString("00000000-0000-0000-0000-000000000019"),
        "Revisar las condiciones", true, RequirementRule.MANUAL_CONFIRMATION, null)),
    listOf(DeliverableSpec(UUID.fromString("00000000-0000-0000-0000-000000000020"), "Video", "Mostrar la experiencia", 1, Instant.parse("2030-02-05T18:00:00Z"))))

@Preview(name = "Postular a campaña", showBackground = true, showSystemUi = true)
@Composable private fun ApplicationFormPreview() = preview { OwnApplicationFormScreen(OwnApplicationFormUiState(
    campaignId = previewCampaign.summary.id, campaign = previewCampaign, checkedAbsence = true), Instant.parse("2030-01-01T00:00:00Z")) }

@Preview(name = "Mis postulaciones", showBackground = true, showSystemUi = true)
@Composable private fun MyApplicationsPreview() = preview { OwnApplicationsScreen(OwnApplicationsListUiState(page = Page(listOf(previewApplication), 1, 0, 20))) }

@Preview(name = "Detalle de postulación", showBackground = true, showSystemUi = true)
@Composable private fun ApplicationDetailPreview() = preview { OwnApplicationDetailScreen(OwnApplicationDetailUiState(previewApplication.id, previewApplication)) }

@Preview(name = "Postulación • Conflicto de versión", showBackground = true, showSystemUi = true)
@Composable private fun ApplicationConflictPreview() = preview { OwnApplicationFormScreen(OwnApplicationFormUiState(
    campaignId = previewCampaign.summary.id, campaign = previewCampaign, application = previewApplication,
    proposal = ApplicationProposal("Mi propuesta local sin guardar", previewApplication.confirmedRequirementIds),
    latest = previewApplication.copy(message = "Propuesta actual del servidor", version = 2),
    failure = ApiFailure(FailureKind.CONFLICT, "CONCURRENT_UPDATE", "La versión del servidor cambió.")), Instant.parse("2030-01-01T00:00:00Z")) }

@Preview(name = "Postulación • Cancelada", showBackground = true, showSystemUi = true)
@Composable private fun CancelledApplicationPreview() = preview { OwnApplicationDetailScreen(OwnApplicationDetailUiState(
    previewApplication.id, previewApplication.copy(status = ApplicationStatus.CANCELLED, version = 1))) }

@Preview(name = "Postulaciones • Error", showBackground = true, showSystemUi = true)
@Composable private fun ApplicationsErrorPreview() = preview { OwnApplicationsScreen(OwnApplicationsListUiState(
    failure = ApiFailure(FailureKind.NETWORK, message = "No se pudo consultar tus postulaciones."))) }

private val previewApplication = Application(UUID.fromString("00000000-0000-0000-0000-000000000021"), previewCampaign.summary.id,
    UUID.fromString("00000000-0000-0000-0000-000000000022"), previewCampaign.summary.title, previewCampaign.summary.brandName,
    "Propongo un video mostrando mi experiencia en el local.", ApplicationStatus.PENDING, Instant.parse("2030-01-01T00:00:00Z"),
    previewCampaign.requirements.map { it.id }.toSet(), 0)

@Preview(name = "Campañas de empresa", showBackground = true, showSystemUi = true)
@Composable private fun BrandCampaignsPreview() = preview { OwnCampaignsScreen(OwnCampaignsUiState(), CampaignEditorUiState(draft = CampaignDraft(), restoring = false)) }

@Preview(name = "Crear campaña", showBackground = true, showSystemUi = true)
@Composable private fun CampaignFormPreview() = preview { CampaignBasicsScreen(CampaignEditorUiState(draft = CampaignDraft(), restoring = false)) }

@Preview(name = "Términos de campaña", showBackground = true, showSystemUi = true)
@Composable private fun CampaignTermsPreview() = preview { CampaignConditionsScreen(CampaignEditorUiState(draft = CampaignDraft(step = 2), restoring = false)) }

@Preview(name = "Campaña • Éxito parcial", showBackground = true, showSystemUi = true)
@Composable private fun PartialCampaignPreview() = preview { CampaignConditionsScreen(CampaignEditorUiState(
    draft = CampaignDraft(serverId = UUID.fromString("00000000-0000-0000-0000-000000000015"), pending = DraftOperation.PUBLISH, step = 2),
    restoring = false, failure = ApiFailure(FailureKind.TIMEOUT, message = "No se pudo confirmar la publicación."),
    notice = "El borrador y sus condiciones se conservan. Comprueba el servidor antes de repetir.")) }

@Preview(name = "Mis campañas • Error", showBackground = true, showSystemUi = true)
@Composable private fun OwnCampaignsErrorPreview() = preview { OwnCampaignsScreen(
    OwnCampaignsUiState(failure = ApiFailure(FailureKind.NETWORK, message = "No se pudo cargar la lista.")),
    CampaignEditorUiState(draft = CampaignDraft(), restoring = false)) }

@Preview(name = "Postulantes", showBackground = true, showSystemUi = true)
@Composable private fun ApplicantsPreview() = preview { ApplicantsScreen(AppState()) }

@Preview(name = "Detalle de postulante", showBackground = true, showSystemUi = true)
@Composable private fun ApplicantDetailPreview() = preview { ApplicantDetailScreen(AppState()) }

@Preview(name = "Acuerdo", showBackground = true, showSystemUi = true)
@Composable private fun AgreementPreview() = preview { AgreementScreen(AppState()) }

@Preview(name = "Colaboraciones", showBackground = true, showSystemUi = true)
@Composable private fun CollaborationsPreview() = preview { CollaborationsScreen(AppState()) }

@Preview(name = "Detalle de colaboración", showBackground = true, showSystemUi = true)
@Composable private fun CollaborationDetailPreview() = preview { CollaborationDetailScreen(AppState()) }

@Preview(name = "Enviar evidencia", showBackground = true, showSystemUi = true)
@Composable private fun EvidenceFormPreview() = preview { EvidenceFormScreen(AppState()) }

@Preview(name = "Revisar entrega", showBackground = true, showSystemUi = true)
@Composable private fun ReviewDeliverablePreview() = preview { ReviewDeliverableScreen(AppState()) }

@Preview(name = "Incidencias", showBackground = true, showSystemUi = true)
@Composable private fun IncidentsPreview() = preview { IncidentsScreen(AppState()) }

@Preview(name = "Reportar incidencia", showBackground = true, showSystemUi = true)
@Composable private fun IncidentFormPreview() = preview { IncidentFormScreen(AppState()) }

@Preview(name = "Métodos de pago", showBackground = true, showSystemUi = true)
@Composable private fun PaymentMethodsPreview() = preview { PaymentMethodsScreen(AppState()) }

@Preview(name = "Agregar método de pago", showBackground = true, showSystemUi = true)
@Composable private fun PaymentFormPreview() = preview { PaymentFormScreen(AppState()) }

@Preview(name = "Planes", showBackground = true, showSystemUi = true)
@Composable private fun PlansPreview() = preview { PlansScreen(AppState()) }

@Preview(name = "Suscripción", showBackground = true, showSystemUi = true)
@Composable private fun SubscriptionPreview() = preview { SubscriptionScreen(AppState()) }

@Preview(name = "Compensación", showBackground = true, showSystemUi = true)
@Composable private fun CompensationPreview() = preview { CompensationScreen(AppState()) }

@Preview(name = "Resultados", showBackground = true, showSystemUi = true)
@Composable private fun ResultsPreview() = preview { ResultsScreen(AppState()) }

@Preview(name = "Historial", showBackground = true, showSystemUi = true)
@Composable private fun HistoryPreview() = preview { HistoryScreen(AppState()) }

@Preview(name = "Detalle del historial", showBackground = true, showSystemUi = true)
@Composable private fun HistoryDetailPreview() = preview { HistoryDetailScreen(AppState()) }

@Preview(name = "Inicio público • English", showBackground = true, showSystemUi = true)
@Composable private fun WelcomeEnglishPreview() = preview {
    WelcomeScreen(AppState().apply { language = "EN" })
}

@Composable
private fun preview(content: @Composable () -> Unit) {
    CollabProTheme(content = content)
}
