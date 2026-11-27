package com.example.collabpro.features.performance.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.features.performance.domain.model.*
import com.example.collabpro.features.performance.presentation.workspace.AttributionSummary
import com.example.collabpro.features.performance.presentation.workspace.SnapshotFacts
import com.example.collabpro.navigation.AppState
import com.example.collabpro.navigation.Route

@Composable
fun ResultsSample() {
    val end = java.time.Instant.parse("2026-11-27T16:00:00Z")
    val period = Period(end.minusSeconds(86400), end)
    val id = java.util.UUID(0L, 1L)
    Page("Resultados", subtitle = "Demostración") {
        item {
            Panel("Medición automática") {
                SnapshotFacts(Snapshot(id, id, "AUTOMATED", "tiktok", "LIFETIME", end, period, mapOf("VIEWS" to 125L)))
            }
        }
        item {
            AttributionSummary(AttributionReport(id, "NO_INTERACTIONS", period, "EXCLUSIVE", Counts(0, 0, 0), Counts(0, 1, 0),
                com.example.collabpro.core.domain.Page(emptyList(), 0, 0, 20),
                mapOf("INQUIRY" to "BRAND_DECLARATION"), "Estimación basada en interacciones registradas."))
        }
    }
}

@Composable
fun HistoryScreen(app: AppState) {
    var empty by remember { mutableStateOf(false) }
    Page("Historial", subtitle = "Revisa acuerdos y resultados anteriores.", onBack = app::back) {
        item { Action(if (empty) "Mostrar historial de ejemplo" else "Ver estado vacío", { empty = !empty }, secondary = true) }
        if (empty) item { Panel("Aún sin colaboraciones") { Text("Cuando finalices una colaboración, aparecerá aquí con sus acuerdos, entregables y resultados.") } }
        else {
            item { LinkCard("Café de barrio", "Café Norte • Finalizada el 12 oct 2026", { app.go(Route.HISTORY_DETAIL) }, "Finalizada") }
            item { LinkCard("Sabores que conectan", "Maki House • Finalizada el 28 oct 2026", { app.go(Route.HISTORY_DETAIL) }, "Finalizada") }
        }
    }
}

@Composable
fun HistoryDetailScreen(app: AppState) {
    Page("Colaboración finalizada", subtitle = "Resumen del acuerdo y su cumplimiento.", onBack = app::back) {
        item { Panel("Acuerdo") { InfoRow("Marca", "Café Norte"); InfoRow("Creador", "Camila Rojas"); InfoRow("Entregables", "2 historias"); InfoRow("Compensación", "Canje de productos") } }
        item { Panel("Cumplimiento") { InfoRow("Publicación", "Realizada"); InfoRow("Validación", "Aprobada"); InfoRow("Compensación", "Entregada") } }
        item { Action("Ver resultados", { app.go(Route.RESULTS) }, secondary = true) }
    }
}
