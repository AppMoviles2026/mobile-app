package com.example.collabpro.features.performance.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.navigation.AppState
import com.example.collabpro.navigation.Route

@Composable
fun ResultsScreen(app: AppState) {
    var mode by remember { mutableStateOf("Disponibles") }
    Page("Resultados", subtitle = "Consulta cifras verificables y su procedencia.", onBack = app::back) {
        item { ChoiceRow(listOf("Disponibles", "Sin métricas", "Evidencia", "Atribución"), mode) { mode = it } }
        when (mode) {
            "Disponibles" -> {
                item { Panel("Rendimiento de la publicación") { app.sampleMetrics.forEach { InfoRow(it.label, it.value) }; InfoRow("Fuente", app.sampleMetrics.first().source); InfoRow("Periodo", app.sampleMetrics.first().period) } }
                item { Notice("Las métricas reflejan el periodo y la fuente indicados; no equivalen directamente a ventas.") }
            }
            "Sin métricas" -> item { Panel("Métricas no disponibles") { Text("Esta red social no proporciona métricas autorizadas para esta publicación."); Text("Puedes consultar evidencias manuales si fueron aportadas por el creador.") } }
            "Evidencia" -> item { Panel("Referencia aportada por creador") { InfoRow("Tipo", "Captura de estadísticas"); InfoRow("Fecha", "25 oct 2026"); InfoRow("Origen", "Evidencia manual"); Text("Esta información no se obtuvo automáticamente y debe revisarse como referencia.") } }
            else -> item { Panel("Resultados atribuibles") { InfoRow("Enlace/código", "MAKI-CAMILA"); InfoRow("Clics", "93"); InfoRow("Consultas", "14"); InfoRow("Periodo", "18–25 oct 2026"); Text("Estimación: estas acciones no prueban por sí solas compras causadas por la colaboración.") } }
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
