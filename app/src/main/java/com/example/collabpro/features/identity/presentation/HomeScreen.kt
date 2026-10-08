package com.example.collabpro.features.identity.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.navigation.AppState
import com.example.collabpro.navigation.Route

@Composable
fun HomeScreen(app: AppState, brand: Boolean, onSignOut: () -> Unit = {}) {
    val account = app.authenticatedAccount
    if (account != null) {
        Page("Hola, ${account.name}", eyebrow = if (brand) "ESPACIO EMPRESA" else "ESPACIO CREADOR", subtitle = "Tu sesión está activa y verificada.") {
            item { Panel("Tu cuenta") {
                InfoRow("Nombre", account.name)
                InfoRow("Tipo", if (brand) "Empresa" else "Creador")
                Text("El tipo de cuenta y el acceso fueron confirmados por el servidor.")
            } }
            item { Notice("Registro, login y recuperación ya están conectados. Las funciones siguientes conservan sus vistas previas; sus datos aún no corresponden a tu cuenta.") }
            item { LinkCard(if (brand) "Mis campañas" else "Explorar campañas", "Vista previa • integración pendiente", { app.go(if (brand) Route.BRAND_CAMPAIGNS else Route.CAMPAIGN_SEARCH) }) }
            item { LinkCard("Mi perfil", "Vista previa • integración pendiente", { app.go(if (brand) Route.BRAND_PROFILE else Route.CREATOR_PROFILE) }) }
            if (!brand) item { LinkCard("Mis postulaciones", "Vista previa • integración pendiente", { app.go(Route.MY_APPLICATIONS) }) }
            item { Action("Cerrar sesión", onSignOut, secondary = true) }
        }
        return
    }
    Page(if (brand) "Hola, ${app.brandProfile.name}" else "Hola, ${app.creatorProfile.name.substringBefore(' ')}", eyebrow = if (brand) "ESPACIO EMPRESA" else "ESPACIO CREADOR", subtitle = if (brand) "Tu próxima gran colaboración empieza aquí." else "Encuentra oportunidades hechas para ti.") {
        item { Panel("Resumen") {
            InfoRow(if (brand) "Campañas activas" else "Postulaciones pendientes", if (brand) "2" else "1")
            InfoRow("Colaboraciones en curso", "1")
            InfoRow(if (brand) "Entregables por revisar" else "Entregables por enviar", "1")
        } }
        if (brand) {
            item { Action("Crear campaña") { app.go(Route.CAMPAIGN_FORM) } }
            item { LinkCard("Revisar postulaciones", "Tienes creadores interesados en Sabores que conectan.", { app.go(Route.APPLICANTS) }, "Nueva actividad") }
            item { LinkCard("Validar entregable", "Camila compartió contenido para revisión.", { app.go(Route.REVIEW_DELIVERABLE) }, "Pendiente") }
            item { LinkCard("Resultados", "Consulta métricas, evidencia y atribución.", { app.go(Route.RESULTS) }) }
            item { LinkCard("Plan y suscripción", "Revisa planes, medio de pago y estado del plan.", { app.go(Route.PLANS) }) }
        } else {
            item { Action("Explorar campañas") { app.go(Route.CAMPAIGN_SEARCH) } }
            item { LinkCard("Mis postulaciones", "Sigue tus solicitudes y edita las pendientes.", { app.go(Route.MY_APPLICATIONS) }, "1 pendiente") }
            item { LinkCard("Acuerdo por confirmar", "Revisa las condiciones antes de iniciar la colaboración.", { app.go(Route.AGREEMENT) }, "Requiere tu aceptación") }
            item { LinkCard("Entregar contenido", "Tu colaboración con Maki House tiene una próxima fecha.", { app.go(Route.EVIDENCE_FORM) }, "18 oct") }
            item { LinkCard("Compensación", "Revisa cuándo y cómo recibirás lo acordado.", { app.go(Route.COMPENSATION) }) }
        }
        item { LinkCard("Historial", "Consulta tus colaboraciones finalizadas.", { app.go(Route.HISTORY) }) }
        item { LinkCard("Centro de incidencias", "Consulta y registra desacuerdos.", { app.go(Route.INCIDENTS) }) }
        item { LinkCard("Medios de pago", "Consulta el estado de tu medio vinculado.", { app.go(Route.PAYMENT_METHODS) }) }
        item { Action("Cerrar sesión", onSignOut, secondary = true) }
    }
}
