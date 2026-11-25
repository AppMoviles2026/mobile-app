package com.example.collabpro.features.identity.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.features.identity.domain.model.*

/** Real account and callback-only UI. Sample data belongs exclusively to PreviewScreens. */
@Composable
fun HomeScreen(account: Account, onSignOut: () -> Unit = {}, onCampaigns: () -> Unit = {}, onProfile: () -> Unit = {},
    onApplications: () -> Unit = {}, summary: (@Composable () -> Unit)? = null, onBilling: (() -> Unit)? = null, opportunities: (@Composable () -> Unit)? = null) {
    val brand = account.accountType == AccountType.BRAND
    Page("Hola, ${account.name}", eyebrow = if (brand) "ESPACIO EMPRESA" else "ESPACIO CREADOR", subtitle = "Tu sesión está activa y verificada.") {
        item { Panel("Tu cuenta") {
            InfoRow("Nombre", account.name); InfoRow("Tipo", if (brand) "Empresa" else "Creador")
            InfoRow("Estado", account.status.displayLabel())
            Text("El tipo de cuenta y el acceso fueron confirmados por el servidor.")
        } }
        if (onBilling != null) item {
            LinkCard(if (brand) "Pagos y suscripción" else "Cuenta receptora",
                if (brand) "Gestionar medios y planes PayPal Sandbox" else "Gestionar la cuenta PayPal Sandbox que recibe compensaciones", onBilling)
        }
        if (summary != null) item { summary() }
        if (!brand && opportunities != null) item { opportunities() }
        item { LinkCard(if (brand) "Mis campañas" else "Explorar campañas", if (brand) "Crear, retomar y publicar tus campañas" else "Buscar oportunidades y consultar condiciones", onCampaigns) }
        item { LinkCard(if (brand) "Perfil empresarial" else "Mi perfil", if (brand) "Consultar y editar los datos del negocio" else "Editar perfil y vincular redes", onProfile) }
        if (!brand) item { LinkCard("Mis postulaciones", "Consultar propuestas y gestionar las pendientes", onApplications) }
        item { Notice("El panel de resultados se encuentra en etapa de prototipo.") }
        item { Action("Cerrar sesión", onSignOut, secondary = true) }
    }
}

private fun AccountStatus.displayLabel() = when (this) { AccountStatus.ACTIVE -> "Activa"; AccountStatus.PENDING -> "Pendiente"; AccountStatus.SUSPENDED -> "Suspendida"; AccountStatus.DISABLED -> "Deshabilitada" }
