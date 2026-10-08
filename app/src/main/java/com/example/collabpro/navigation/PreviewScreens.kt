package com.example.collabpro.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.collabpro.features.billing.presentation.*
import com.example.collabpro.features.campaign.presentation.*
import com.example.collabpro.features.collaboration.presentation.*
import com.example.collabpro.features.identity.presentation.*
import com.example.collabpro.features.identity.presentation.auth.*
import com.example.collabpro.features.identity.domain.model.AccountType
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
@Composable private fun BrandProfilePreview() = preview { ProfileScreen(AppState(), true) }

@Preview(name = "Perfil • Creador", showBackground = true, showSystemUi = true)
@Composable private fun CreatorProfilePreview() = preview { ProfileScreen(AppState(), false) }

@Preview(name = "Cuentas sociales", showBackground = true, showSystemUi = true)
@Composable private fun SocialAccountsPreview() = preview { SocialAccountsScreen(AppState()) }

@Preview(name = "Buscar campañas", showBackground = true, showSystemUi = true)
@Composable private fun CampaignSearchPreview() = preview { CampaignSearchScreen(AppState()) }

@Preview(name = "Detalle de campaña", showBackground = true, showSystemUi = true)
@Composable private fun CampaignDetailPreview() = preview { CampaignDetailScreen(AppState()) }

@Preview(name = "Postular a campaña", showBackground = true, showSystemUi = true)
@Composable private fun ApplicationFormPreview() = preview { ApplicationFormScreen(AppState()) }

@Preview(name = "Mis postulaciones", showBackground = true, showSystemUi = true)
@Composable private fun MyApplicationsPreview() = preview { MyApplicationsScreen(AppState()) }

@Preview(name = "Campañas de empresa", showBackground = true, showSystemUi = true)
@Composable private fun BrandCampaignsPreview() = preview { BrandCampaignsScreen(AppState()) }

@Preview(name = "Crear campaña", showBackground = true, showSystemUi = true)
@Composable private fun CampaignFormPreview() = preview { CampaignFormScreen(AppState()) }

@Preview(name = "Términos de campaña", showBackground = true, showSystemUi = true)
@Composable private fun CampaignTermsPreview() = preview { CampaignTermsScreen(AppState()) }

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
