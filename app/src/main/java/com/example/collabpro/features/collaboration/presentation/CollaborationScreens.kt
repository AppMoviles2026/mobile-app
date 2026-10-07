package com.example.collabpro.features.collaboration.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.navigation.AppState
import com.example.collabpro.navigation.Route
import com.example.collabpro.navigation.UserRole

@Composable
fun AgreementScreen(app: AppState) {
    var accepted by remember { mutableStateOf(false) }
    var otherAccepted by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf(false) }
    Page("Confirmar colaboración", subtitle = "Ambas partes deben aceptar estas condiciones antes de comenzar.", onBack = app::back) {
        item { Panel("Sabores que conectan") { InfoRow("Marca", "Maki House"); InfoRow("Creadora", "Camila Rojas"); InfoRow("Entregables", "1 video + 2 historias"); InfoRow("Fecha", "18 oct 2026"); InfoRow("Compensación", "Canje + S/ 180") } }
        item { Panel("Aceptación") { InfoRow("Tu confirmación", if (accepted) "Aceptada" else "Pendiente"); InfoRow("Otra parte", if (otherAccepted) "Aceptada" else "Pendiente") } }
        if (rejected) item { Notice("Una parte rechazó las condiciones. La colaboración no se inicia.") }
        else if (accepted && otherAccepted) item { Notice("Acuerdo confirmado por ambas partes. La colaboración puede comenzar.") }
        item { Action("Acepto las condiciones") { accepted = true } }
        item { Action("Ver ambas partes confirmadas", { accepted = true; otherAccepted = true }, secondary = true) }
        item { Action("Rechazar condiciones", { rejected = true }, secondary = true) }
        if (accepted && otherAccepted) item { Action("Ver colaboración") { app.go(Route.COLLABORATION_DETAIL) } }
    }
}

@Composable
fun CollaborationsScreen(app: AppState) {
    Page("Colaboraciones", subtitle = "Revisa avances, tareas pendientes y fechas.", onBack = app::back) {
        item { LinkCard(app.activeCollaboration.title, "${app.activeCollaboration.partner} • Entrega: ${app.activeCollaboration.dueDate}", { app.go(Route.COLLABORATION_DETAIL) }, app.activeCollaboration.status) }
        item { LinkCard("Café de barrio", "Café Norte × Camila Rojas • Entrega: 12 oct", { app.go(Route.COLLABORATION_DETAIL); app.variant = "Vencida" }, "Vencida") }
    }
}

@Composable
fun CollaborationDetailScreen(app: AppState) {
    val expired = app.variant == "Vencida"
    Page(if (expired) "Café de barrio" else app.activeCollaboration.title, eyebrow = "COLABORACIÓN", subtitle = app.activeCollaboration.partner, onBack = app::back) {
        item { Status(if (expired) "Vencida" else "En ejecución") }
        if (expired) item { Notice("La fecha límite se superó. Revisa la incidencia o acuerda los próximos pasos.") }
        item { Panel("Seguimiento") { InfoRow("Acuerdo", "Aceptado por ambas partes"); InfoRow("Contenido", if (expired) "Fuera de plazo" else "Pendiente de entrega"); InfoRow("Validación", "Pendiente"); InfoRow("Compensación", "Pendiente") } }
        item { Panel("Próxima acción") { Text(if (app.role == UserRole.CREATOR) "Publica el contenido y adjunta evidencia antes de la fecha límite." else "Espera la evidencia y revisa los entregables cuando lleguen.") } }
        if (app.role == UserRole.CREATOR) item { Action("Entregar contenido y evidencia") { app.go(Route.EVIDENCE_FORM) } }
        else item { Action("Revisar entregable") { app.go(Route.REVIEW_DELIVERABLE) } }
        item { Action("Ver compensación", { app.go(Route.COMPENSATION) }, secondary = true) }
        item { Action("Incidencias", { app.go(Route.INCIDENTS) }, secondary = true) }
        if (app.role == UserRole.BRAND) item { Action("Ver resultados", { app.go(Route.RESULTS) }, secondary = true) }
    }
}

@Composable
fun EvidenceFormScreen(app: AppState) {
    var url by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var status by remember { mutableStateOf("") }
    Page("Entregar contenido", subtitle = "Adjunta el enlace publicado y la evidencia acordada.", onBack = app::back) {
        item { Panel("Entregable") { InfoRow("Formato", "1 video + 2 historias"); InfoRow("Fecha límite", "18 oct 2026") } }
        item { Entry("Enlace a la publicación", url, { url = it }) }
        item { Entry("Evidencia o enlace de captura", note, { note = it }, singleLine = false) }
        if (status.isNotBlank()) item { Notice(status) }
        item { Action("Presentar entrega") { status = if (url.isBlank() || note.isBlank()) "Completa el contenido y la evidencia." else "Entrega registrada: pendiente de validación (vista previa)." } }
        item { Action("Ver entrega fuera de plazo", { status = "Entrega recibida fuera del plazo acordado." }, secondary = true) }
        item { Action("Presentar corrección", { status = "Nueva versión registrada para validación (vista previa)." }, secondary = true) }
    }
}

@Composable
fun ReviewDeliverableScreen(app: AppState) {
    var status by remember { mutableStateOf("Pendiente de revisión") }; var reason by remember { mutableStateOf("") }
    Page("Validar entregable", subtitle = "Contrasta la entrega con las condiciones del acuerdo.", onBack = app::back) {
        item { Status(status) }
        item { Panel("Evidencia recibida") { InfoRow("Creadora", "Camila Rojas"); InfoRow("Contenido", "Video + 2 historias"); InfoRow("Publicación", "instagram.com/reel/ejemplo"); InfoRow("Fecha", "17 oct 2026") } }
        item { Panel("Criterios") { Text("• Formato acordado"); Text("• Mensaje y producto correctos"); Text("• Publicación y enlace verificables"); Text("• Declaración comercial visible") } }
        item { Entry("Observaciones para la creadora", reason, { reason = it }, singleLine = false) }
        item { Action("Aprobar entregable") { status = "Aprobado • compensación habilitada" } }
        item { Action("Solicitar corrección", { status = if (reason.isBlank()) "Escribe una observación para solicitar corrección." else "Rechazado • corrección solicitada" }, secondary = true) }
        item { Action("Ver nueva versión", { status = "Nueva versión pendiente de revisión" }, secondary = true) }
    }
}

@Composable
fun IncidentsScreen(app: AppState) {
    var status by remember { mutableStateOf("En revisión") }
    Page("Incidencias", subtitle = "Da seguimiento a los desacuerdos de esta colaboración.", onBack = app::back) {
        item { Action("Registrar incidencia") { app.go(Route.INCIDENT_FORM) } }
        item { Panel("Entrega fuera de plazo") { Status(status); Text("La marca y la creadora están revisando el cumplimiento de la fecha."); InfoRow("Registrada", "19 oct 2026") } }
        item { Action("Ver resolución registrada", { status = "Resuelta • acuerdo actualizado" }, secondary = true) }
    }
}

@Composable
fun IncidentFormScreen(app: AppState) {
    var category by remember { mutableStateOf("Condiciones") }; var detail by remember { mutableStateOf("") }; var status by remember { mutableStateOf("") }
    Page("Nueva incidencia", subtitle = "Describe qué ocurrió y qué condición fue afectada.", onBack = app::back) {
        item { ChoiceRow(listOf("Condiciones", "Entregable", "Compensación"), category) { category = it } }
        item { Entry("Descripción del problema", detail, { detail = it }, singleLine = false) }
        item { LocalEntry("Evidencia o enlace (opcional)") }
        if (status.isNotBlank()) item { Notice(status) }
        item { Action("Registrar incidencia") { status = if (detail.isBlank()) "Describe el problema antes de continuar." else "Incidencia registrada en el historial (vista previa)." } }
    }
}
