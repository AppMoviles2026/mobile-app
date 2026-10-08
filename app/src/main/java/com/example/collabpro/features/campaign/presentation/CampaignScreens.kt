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

// Applicant selection remains a prototype for stories outside this delivery.

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
