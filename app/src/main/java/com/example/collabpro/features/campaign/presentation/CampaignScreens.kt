package com.example.collabpro.features.campaign.presentation

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
fun BrandCampaignsScreen(app: AppState) {
    Page("Mis campañas", subtitle = "Organiza tus oportunidades y revisa las postulaciones.", onBack = app::back) {
        item { Action("Crear campaña") { app.go(Route.CAMPAIGN_FORM) } }
        app.campaigns.forEach { campaign -> item {
            LinkCard(campaign.title, "${campaign.category} • ${campaign.deadline} • ${campaign.compensation}", { app.selectCampaign(campaign.id); app.go(Route.CAMPAIGN_DETAIL) }, campaign.status)
        } }
    }
}

@Composable
fun CampaignFormScreen(app: AppState) {
    var title by remember { mutableStateOf("") }; var objective by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Gastronomía") }; var audience by remember { mutableStateOf("") }; var error by remember { mutableStateOf(false) }
    Page("Nueva campaña", eyebrow = "PASO 1 DE 2", subtitle = "Describe el objetivo y a quién quieres llegar.", onBack = app::back) {
        item { Entry("Título de campaña", title, { title = it }, error = error && title.isBlank()) }
        item { Entry("Objetivo", objective, { objective = it }, singleLine = false, error = error && objective.isBlank()) }
        item { ChoiceRow(listOf("Gastronomía", "Belleza", "Moda", "Servicios"), category) { category = it } }
        item { Entry("Público objetivo", audience, { audience = it }, error = error && audience.isBlank()) }
        if (error) item { Notice("Completa el título, objetivo y público antes de continuar.") }
        item { Action("Continuar a condiciones") { error = title.isBlank() || objective.isBlank() || audience.isBlank(); if (!error) app.go(Route.CAMPAIGN_TERMS) } }
        item { Notice("La campaña será visible cuando completes también sus condiciones.") }
    }
}

@Composable
fun CampaignTermsScreen(app: AppState) {
    var requirements by remember { mutableStateOf("") }; var deliverables by remember { mutableStateOf("") }; var deadline by remember { mutableStateOf("") }; var compensation by remember { mutableStateOf("") }; var error by remember { mutableStateOf(false) }; var published by remember { mutableStateOf(false) }; var incompatible by remember { mutableStateOf(false) }
    Page("Condiciones de campaña", eyebrow = "PASO 2 DE 2", subtitle = "Define exactamente qué esperas y qué recibirá el creador.", onBack = app::back) {
        item { Entry("Requisitos del creador", requirements, { requirements = it }, singleLine = false) }
        item { Entry("Entregables", deliverables, { deliverables = it }, singleLine = false) }
        item { Entry("Fecha límite (dd/mm/aaaa)", deadline, { deadline = it }) }
        item { Entry("Compensación (monto o canje)", compensation, { compensation = it }) }
        if (error) item { Notice("Completa las condiciones y usa una fecha válida antes de publicar.") }
        if (incompatible) item { Notice("Estas condiciones son incompatibles con las fechas de la campaña. Revísalas antes de guardar.") }
        if (published) item { Notice("Vista previa de campaña publicada. Encontrarás tus campañas en el panel de empresa.") }
        item { Action("Publicar campaña") { error = requirements.isBlank() || deliverables.isBlank() || !deadline.contains('/') || compensation.isBlank(); published = !error } }
        item { Action("Ver condiciones incompatibles", { incompatible = true }, secondary = true) }
        if (published) item { Action("Ir a mis campañas", { app.go(Route.BRAND_CAMPAIGNS) }, secondary = true) }
    }
}

@Composable
fun CampaignSearchScreen(app: AppState) {
    var query by remember { mutableStateOf("") }; var filter by remember { mutableStateOf("Todas") }
    val matches = app.campaigns.filter { (filter == "Todas" || it.category == filter) && (query.isBlank() || it.title.contains(query, true) || it.brand.contains(query, true)) }
    Page("Explorar campañas", subtitle = "Oportunidades alineadas con tu contenido.", onBack = app::back) {
        item { Entry("Buscar marca o campaña", query, { query = it }) }
        item { ChoiceRow(listOf("Todas", "Gastronomía", "Belleza", "Moda"), filter) { filter = it } }
        if (matches.isEmpty()) item { Panel("Sin coincidencias") { Text("Prueba otra palabra o categoría."); Action("Limpiar filtros", { query = ""; filter = "Todas" }, secondary = true) } }
        matches.forEach { campaign -> item { LinkCard(campaign.title, "${campaign.brand} • ${campaign.location}\n${campaign.compensation}", { app.selectCampaign(campaign.id); app.go(Route.CAMPAIGN_DETAIL) }, campaign.status) } }
    }
}

@Composable
fun CampaignDetailScreen(app: AppState) {
    val campaign = app.campaigns.firstOrNull { it.id == app.campaignId } ?: app.campaigns.first()
    Page(campaign.title, eyebrow = campaign.brand.uppercase(), subtitle = campaign.summary, onBack = app::back) {
        item { Status(campaign.status) }
        item { Panel("Información") { InfoRow("Categoría", campaign.category); InfoRow("Ubicación", campaign.location); InfoRow("Fecha límite", campaign.deadline); InfoRow("Compensación", campaign.compensation) } }
        item { Panel("Condiciones") { Text("Objetivo: llegar a una audiencia joven interesada en experiencias locales."); Text("Requisito: perfil auténtico y afinidad con la categoría."); Text("Entregables: un video corto y dos historias con enlace de publicación."); Text("Aceptación: contenido publicado y evidencia verificable.") } }
        if (campaign.status == "Cerrada") item { Notice("Esta campaña ya no admite nuevas postulaciones.") }
        else if (app.role == com.example.collabpro.navigation.UserRole.CREATOR) item { Action("Postular a esta campaña") { app.go(Route.APPLICATION_FORM) } }
        else {
            item { Action("Revisar postulaciones") { app.go(Route.APPLICANTS) } }
            item { Action("Ver estado de colaboración", { app.go(Route.COLLABORATION_DETAIL) }, secondary = true) }
        }
    }
}

@Composable
fun ApplicationFormScreen(app: AppState) {
    var message by remember { mutableStateOf("") }; var state by remember { mutableStateOf("Nueva") }
    Page(if (app.variant == "edit") "Editar postulación" else "Postular a campaña", subtitle = "Presenta tu experiencia y confirma que puedes cumplir las condiciones.", onBack = app::back) {
        item { Panel("Campaña") { Text(app.campaigns.firstOrNull { it.id == app.campaignId }?.title ?: "Sabores que conectan"); InfoRow("Entregables", "1 video + 2 historias"); InfoRow("Estado", "Abierta") } }
        item { Entry("Tu propuesta", message, { message = it }, singleLine = false) }
        item { Notice("Al postular, confirmas que revisaste requisitos, fechas y compensación.") }
        if (state != "Nueva") item { Notice(state) }
        item { Action(if (app.variant == "edit") "Guardar cambios" else "Enviar postulación") { state = if (message.isBlank()) "Escribe tu propuesta para continuar." else "Postulación pendiente de evaluación (vista previa)." } }
        item { Action("Ver estado: requisito incumplido", { state = "No cumples el requisito obligatorio de audiencia." }, secondary = true) }
        item { Action("Ver estado: postulación duplicada", { state = "Ya existe una postulación para esta campaña." }, secondary = true) }
        item { Action("Mis postulaciones", { app.go(Route.MY_APPLICATIONS) }, secondary = true) }
    }
}

@Composable
fun MyApplicationsScreen(app: AppState) {
    var status by remember { mutableStateOf("Pendiente") }
    Page("Mis postulaciones", subtitle = "Consulta, edita o cancela mientras estén pendientes.", onBack = app::back) {
        item { Panel("Sabores que conectan") { Status(status); Text("Maki House • Gastronomía"); Text("Tu propuesta: puedo presentar los nuevos makis en video y dos historias.") } }
        if (status == "Pendiente") {
            item { Action("Editar postulación") { app.variant = "edit"; app.go(Route.APPLICATION_FORM); app.variant = "edit" } }
            item { Action("Cancelar postulación", { status = "Cancelada" }, secondary = true) }
        }
        item { Action("Ver resultado: seleccionada", { status = "Seleccionada" }, secondary = true) }
        item { Action("Ver resultado: rechazada", { status = "Rechazada" }, secondary = true) }
        item { Action("Explorar más campañas", { app.go(Route.CAMPAIGN_SEARCH) }, secondary = true) }
    }
}

@Composable
fun ApplicantsScreen(app: AppState) {
    Page("Postulaciones", subtitle = "Compara los perfiles antes de elegir.", onBack = app::back) {
        item { LinkCard("Camila Rojas", "Food & lifestyle • 8.4 mil seguidores • Lima", { app.go(Route.APPLICANT_DETAIL) }, "Pendiente") }
        item { LinkCard("Diego Salazar", "Gastronomía • 12 mil seguidores • Lima", { app.go(Route.APPLICANT_DETAIL) }, "Pendiente") }
    }
}

@Composable
fun ApplicantDetailScreen(app: AppState) {
    var state by remember { mutableStateOf("Pendiente") }
    Page("Camila Rojas", eyebrow = "POSTULANTE", subtitle = "Food & lifestyle • Instagram y TikTok", onBack = app::back) {
        item { Status(state) }
        item { Panel("Perfil") { InfoRow("Audiencia", "8.4 mil seguidores"); InfoRow("Ubicación", "Lima"); InfoRow("Nicho", "Gastronomía local"); Text("Propongo un video de degustación y dos historias con enlace.") } }
        item { Action("Seleccionar creadora") { state = "Seleccionada"; app.go(Route.AGREEMENT) } }
        item { Action("Rechazar postulación", { state = "Rechazada • resultado listo para comunicar" }, secondary = true) }
    }
}
