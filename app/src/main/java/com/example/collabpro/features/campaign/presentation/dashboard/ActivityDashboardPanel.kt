package com.example.collabpro.features.campaign.presentation.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.features.identity.domain.model.AccountType

@Composable
fun ActivityDashboardPanel(state: ActivityDashboardUiState, onRefresh: () -> Unit = {}) {
    Panel("Tu actividad") {
        if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Consultando resumen…") }
        state.failure?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        state.total?.takeIf { !state.loading && state.failure == null }?.let { total ->
            InfoRow(if (state.type == AccountType.BRAND) "Total de campañas" else "Total de postulaciones", total.toString())
            Text(if (state.type == AccountType.BRAND) "Incluye borradores y campañas publicadas o cerradas." else "Incluye todos los estados, también las canceladas.")
        }
        Text("Colaboraciones, entregables, pagos y métricas aún no tienen datos disponibles en esta versión.", style = MaterialTheme.typography.bodySmall)
        Action(if (state.failure == null) "Actualizar resumen" else "Reintentar resumen", onRefresh, secondary = true, enabled = !state.loading)
    }
}
