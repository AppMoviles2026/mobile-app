package com.example.collabpro.features.billing.presentation

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
fun PaymentMethodsScreen(app: AppState) {
    var linked by remember { mutableStateOf(false) }
    Page("Medios de pago", subtitle = "Gestiona el medio requerido para operaciones económicas.", onBack = app::back) {
        if (linked) item { Panel("Visa •••• 4242") { Status("Vinculado"); Text("Medio de demostración") } }
        else item { Notice("Aún no tienes un medio de pago vinculado.") }
        item { Action(if (linked) "Agregar otro medio" else "Agregar medio de pago") { app.go(Route.PAYMENT_FORM) } }
        item { Action("Ver vinculación correcta", { linked = true }, secondary = true) }
        item { Action("Ver estado sin medio", { linked = false }, secondary = true) }
    }
}

@Composable
fun PaymentFormScreen(app: AppState) {
    var number by remember { mutableStateOf("") }; var expiry by remember { mutableStateOf("") }; var status by remember { mutableStateOf("") }
    Page("Vincular medio de pago", subtitle = "Vista previa del formulario de asociación.", onBack = app::back) {
        item { Entry("Número de tarjeta de prueba", number, { number = it }) }
        item { Entry("Vencimiento MM/AA", expiry, { expiry = it }) }
        item { Notice("No ingreses datos reales: esta pantalla es solo una maqueta y no procesa pagos.") }
        if (status.isNotBlank()) item { Notice(status) }
        item { Action("Vincular") { status = if (number.length < 12 || !expiry.contains('/')) "No se pudo validar el medio de pago." else "Medio de demostración vinculado." } }
    }
}

@Composable
fun PlansScreen(app: AppState) {
    var selected by remember { mutableStateOf("Esencial") }
    Page("Planes para empresas", subtitle = "Elige un plan adecuado al tamaño de tus campañas.", onBack = app::back) {
        item { Panel("Esencial") { Text("Para comenzar con campañas pequeñas."); Text("Precio por definir",); Action(if (selected == "Esencial") "Seleccionado" else "Elegir Esencial") { selected = "Esencial" } } }
        item { Panel("Crecimiento") { Text("Para gestionar más colaboraciones y análisis."); Text("Precio por definir"); Action(if (selected == "Crecimiento") "Seleccionado" else "Elegir Crecimiento") { selected = "Crecimiento" } } }
        item { Notice("Los precios y beneficios finales están sujetos a validación comercial.") }
        item { Action("Continuar con $selected") { app.go(Route.SUBSCRIPTION) } }
    }
}

@Composable
fun SubscriptionScreen(app: AppState) {
    var state by remember { mutableStateOf("Pendiente de confirmación") }
    Page("Suscripción", subtitle = "Confirma el plan seleccionado para tu empresa.", onBack = app::back) {
        item { Panel("Resumen") { InfoRow("Plan", app.samplePlan.name); InfoRow("Precio", app.samplePlan.price); InfoRow("Medio", "Visa •••• 4242"); InfoRow("Estado", state) } }
        item { Notice("La confirmación de esta maqueta solo cambia el estado visible. No se realiza ningún cobro.") }
        item { Action("Simular suscripción activa") { state = "Activa" } }
        item { Action("Ver cobro fallido", { state = "Cobro fallido • plan no activado" }, secondary = true) }
        item { Action("Ver operación sin medio", { state = "Agrega un medio de pago para continuar" }, secondary = true) }
        item { Action("Gestionar medio de pago", { app.go(Route.PAYMENT_METHODS) }, secondary = true) }
    }
}

@Composable
fun CompensationScreen(app: AppState) {
    var state by remember { mutableStateOf("Pendiente") }
    Page("Compensación", subtitle = "Estado del valor acordado por la colaboración.", onBack = app::back) {
        item { Status(state) }
        item { Panel(app.activeCollaboration.title) { InfoRow("Tipo", app.sampleCompensation.type); InfoRow("Monto", app.sampleCompensation.amount); InfoRow("Entregables", if (state == "Pagada") "Validados" else "Pendientes de validación"); InfoRow("Estado", state) } }
        if (state == "Pendiente") item { Notice("La compensación no se completa hasta que todos los entregables sean validados.") }
        if (state.contains("incidencia", true)) item { Notice("Una incidencia está afectando la compensación. Consulta su estado para conocer los próximos pasos.") }
        item { Action("Ver estado: pagada") { state = "Pagada" } }
        item { Action("Ver estado: incidencia", { state = "Afectada por incidencia" }, secondary = true) }
        item { Action("Ver estado: pendiente", { state = "Pendiente" }, secondary = true) }
        item { Action("Ir a incidencias", { app.go(Route.INCIDENTS) }, secondary = true) }
    }
}
