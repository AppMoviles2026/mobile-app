package com.example.collabpro.features.identity.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.features.identity.domain.model.*

/** Real account and callback-only UI. Sample data belongs exclusively to PreviewScreens. */
@Composable
fun HomeScreen(account: Account, onSignOut: () -> Unit = {}, onCampaigns: () -> Unit = {}, onProfile: () -> Unit = {},
    onApplications: () -> Unit = {}, summary: (@Composable () -> Unit)? = null, opportunities: (@Composable () -> Unit)? = null) {
    val brand = account.accountType == AccountType.BRAND
    Page("Hola, ${account.name}", eyebrow = if (brand) "ESPACIO EMPRESA" else "ESPACIO CREADOR", subtitle = "Tu sesión está activa y verificada.") {
        item { Panel("Tu cuenta") {
            InfoRow("Nombre", account.name); InfoRow("Tipo", if (brand) "Empresa" else "Creador")
            InfoRow("Estado", account.status.displayLabel())
            Text("El tipo de cuenta y el acceso fueron confirmados por el servidor.")
        } }
        if (summary != null) item { summary() }
        if (!brand && opportunities != null) item { opportunities() }
        item { LinkCard(if (brand) "Mis campañas" else "Explorar campañas", if (brand) "Crear, retomar y publicar tus campañas" else "Buscar oportunidades y consultar condiciones", onCampaigns) }
        item { LinkCard(if (brand) "Mi cuenta" else "Mi perfil", if (brand) "Consultar los datos registrados de tu cuenta" else "Editar perfil y vincular redes", onProfile) }
        if (!brand) item { LinkCard("Mis postulaciones", "Consultar propuestas y gestionar las pendientes", onApplications) }
        item { Notice("Las secciones de colaboración, pagos y resultados son prototipos con datos de ejemplo; todavía no están conectadas.") }
        item { Action("Cerrar sesión", onSignOut, secondary = true) }
    }
}

/** Read-only existing account contract, not the future full company profile (US-12). */
@Composable
fun BrandAccountScreen(account: Account, onBack: () -> Unit = {}) {
    Page("Mi cuenta empresarial", subtitle = "Información confirmada por el servidor.", onBack = onBack) {
        item { Panel("Cuenta") {
            InfoRow("Nombre", account.name); InfoRow("Tipo", "Empresa"); InfoRow("Estado", account.status.displayLabel())
        } }
        item { Notice("La gestión del perfil empresarial completo corresponde a una historia posterior. No hay rubro, biografía ni ubicación empresarial disponibles en este contrato.") }
    }
}
private fun AccountStatus.displayLabel() = when (this) { AccountStatus.ACTIVE -> "Activa"; AccountStatus.PENDING -> "Pendiente"; AccountStatus.SUSPENDED -> "Suspendida"; AccountStatus.DISABLED -> "Deshabilitada" }
