package com.example.collabpro.features.identity.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.navigation.AppState
import com.example.collabpro.navigation.Route
import com.example.collabpro.navigation.UserRole

@Composable
fun WelcomeScreen(app: AppState) {
    val english = app.language == "EN"
    Page(if (english) "Connect brands with real talent" else "Conecta marcas con talento real", subtitle = if (english) "Clear campaigns, relevant creators and visible results in one place." else "Campañas claras, creadores afines y resultados visibles en un solo lugar.") {
        item { ChoiceRow(listOf("ES", "EN"), app.language) { app.language = it } }
        item { Panel(if (english) "I'm a business" else "Soy una empresa") { Text(if (english) "Find creators for your audience and manage campaigns with confidence." else "Encuentra creadores para tu audiencia y gestiona tus campañas con confianza."); Action(if (english) "Explore for businesses" else "Descubrir para empresas") { app.go(Route.ABOUT_BRAND) } } }
        item { Panel(if (english) "I'm a creator" else "Soy creador de contenido") { Text(if (english) "Discover campaigns and review terms before applying." else "Descubre campañas y conoce las condiciones antes de postular."); Action(if (english) "Explore for creators" else "Descubrir para creadores", { app.go(Route.ABOUT_CREATOR) }, secondary = true) } }
        item { Action(if (english) "Create account" else "Crear cuenta", { app.go(Route.ROLE_PICK) }) }
        item { Action(if (english) "Sign in" else "Ya tengo cuenta", { app.go(Route.LOGIN) }, secondary = true) }
        item { TextButton(onClick = { app.go(Route.CONTACT) }) { Text(if (english) "Questions? Contact us →" else "¿Tienes preguntas? Contáctanos →") } }
    }
}

@Composable
fun AboutScreen(app: AppState, brand: Boolean) {
    val english = app.language == "EN"
    Page(if (english) { if (brand) "More control for your brand" else "More opportunities for your content" } else { if (brand) "Más control para tu marca" else "Más oportunidades para tu contenido" }, subtitle = if (english) "Clear terms from the first brief to the final result." else if (brand) "Del primer brief al resultado final, sin conversaciones dispersas." else "Encuentra campañas que encajen contigo y trabaja con condiciones claras.", onBack = app::back) {
        item { Panel(if (english) "What you can do" else "Lo que puedes hacer") {
            (if (english && brand) listOf("Define campaigns and deliverables", "Review applications", "Validate content and compensation", "See sourced results") else if (english) listOf("Find campaigns by niche", "Review terms and compensation", "Apply and submit evidence", "Track payment status") else if (brand) listOf("Definir campañas y entregables", "Revisar postulaciones", "Validar contenido y compensaciones", "Consultar resultados con su fuente") else listOf("Buscar campañas por nicho", "Leer requisitos y compensación", "Postular y entregar evidencias", "Seguir el estado de tu pago")).forEach { Text("• $it") }
        } }
        item { Action(if (english) "How it works" else "Cómo funciona", { app.go(if (brand) Route.HOW_BRAND else Route.HOW_CREATOR) }) }
        item { Action(if (english) "Create my account" else "Crear mi cuenta", { app.selectRole(if (brand) UserRole.BRAND else UserRole.CREATOR); app.go(if (brand) Route.REGISTER_BRAND else Route.REGISTER_CREATOR) }, secondary = true) }
    }
}

@Composable
fun HowScreen(app: AppState, brand: Boolean) {
    val english = app.language == "EN"
    val steps = if (english && brand) listOf("1. Define your campaign" to "Set goals, audience, deliverables, dates and compensation.", "2. Review applicants" to "Compare profiles and select relevant creators.", "3. Confirm the agreement" to "Both parties accept the terms.", "4. Validate and evaluate" to "Review evidence, confirm compensation and analyse results.") else if (english) listOf("1. Find campaigns" to "Search by interest, location and compensation.", "2. Review terms" to "Check requirements and deadlines before applying.", "3. Confirm agreement" to "Accept terms and create the content.", "4. Deliver and get paid" to "Submit evidence and track compensation.") else if (brand) listOf("1. Define tu campaña" to "Objetivo, público, entregables, fechas y compensación.", "2. Revisa postulantes" to "Compara perfiles y selecciona creadores afines.", "3. Formaliza el acuerdo" to "Ambas partes aceptan las condiciones.", "4. Valida y evalúa" to "Revisa evidencias antes de confirmar la compensación y analiza resultados.") else listOf("1. Encuentra campañas" to "Busca por interés, ubicación y tipo de compensación.", "2. Revisa condiciones" to "Conoce requisitos, entregables y fechas antes de postular.", "3. Confirma el acuerdo" to "Acepta las condiciones y produce el contenido.", "4. Entrega y cobra" to "Envía evidencias, revisa la validación y sigue tu compensación.")
    Page(if (english) "How it works" else "Así funciona", subtitle = if (english) "A clear path from opportunity to results" else if (brand) "El recorrido de una marca en CollabPro" else "El recorrido de un creador en CollabPro", onBack = app::back) {
        steps.forEach { (title, detail) -> item { Panel(title) { Text(detail) } } }
        item { Notice(if (english) "Compensation is confirmed after the agreed deliverables are approved." else "La compensación se confirma después de validar los entregables acordados.") }
        item { Action(if (english) "Get started" else "Comenzar", { app.go(if (brand) Route.REGISTER_BRAND else Route.REGISTER_CREATOR) }) }
    }
}

@Composable
fun ContactScreen(app: AppState) {
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }; var submitted by remember { mutableStateOf(false) }; var attempted by remember { mutableStateOf(false) }
    val english = app.language == "EN"
    Page(if (english) "Let's talk" else "Hablemos", subtitle = if (english) "Tell us what you need and our team will reply." else "Cuéntanos qué necesitas y nuestro equipo te responderá.", onBack = app::back) {
        item { Entry(if (english) "Name" else "Nombre", name, { name = it }, error = attempted && name.isBlank()) }
        item { Entry(if (english) "Email" else "Correo electrónico", email, { email = it }, error = attempted && !email.contains('@')) }
        item { Entry(if (english) "Message" else "Consulta", message, { message = it }, singleLine = false, error = attempted && message.isBlank()) }
        if (attempted && !submitted) item { Notice(if (english) "Complete your name, a valid email and your message." else "Completa tu nombre, un correo válido y tu consulta.") }
        if (submitted) item { Notice(if (english) "Preview: your message is ready. Sending will be available after connecting the service." else "Vista previa: tu consulta está lista. El envío real estará disponible al conectar el servicio.") }
        item { Action(if (english) "Send message" else "Enviar consulta") { attempted = true; submitted = name.isNotBlank() && email.contains('@') && message.isNotBlank() } }
    }
}

@Composable
fun RolePickerScreen(app: AppState) {
    val english = app.language == "EN"
    Page(if (english) "How will you use CollabPro?" else "¿Cómo usarás CollabPro?", subtitle = if (english) "Choose your account type to continue." else "Elige tu tipo de cuenta para continuar.", onBack = app::back) {
        item { LinkCard(if (english) "I represent a business" else "Represento una empresa", if (english) "I want to create campaigns and work with creators." else "Quiero crear campañas y trabajar con creadores.", { app.selectRole(UserRole.BRAND); app.go(Route.REGISTER_BRAND) }) }
        item { LinkCard(if (english) "I'm a creator" else "Soy creador", if (english) "I want to discover campaigns and work with brands." else "Quiero descubrir campañas y colaborar con marcas.", { app.selectRole(UserRole.CREATOR); app.go(Route.REGISTER_CREATOR) }) }
    }
}

@Composable
fun BrandProfileScreen(app: AppState) {
    var name by remember { mutableStateOf(app.brandProfile.name) }
    var area by remember { mutableStateOf(app.brandProfile.category) }
    var location by remember { mutableStateOf(app.brandProfile.location) }
    var detail by remember { mutableStateOf(app.brandProfile.description) }
    var saved by remember { mutableStateOf(false) }
    Page("Perfil de empresa", subtitle = "Esta es la información que verán tus posibles colaboradores.", onBack = app::back) {
        item { Entry("Nombre comercial", name, { name = it }) }
        item { Entry("Rubro", area, { area = it }) }
        item { Entry("Ubicación", location, { location = it }) }
        item { Entry("Descripción", detail, { detail = it }, singleLine = false) }
        if (saved) item { Notice("Cambios visibles en esta vista previa.") }
        item { Action("Guardar perfil") { saved = true } }
    }
}
