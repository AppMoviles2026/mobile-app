package com.example.collabpro.features.billing.presentation.sandbox

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.core.domain.Page as ResultPage
import com.example.collabpro.features.billing.application.PayPalReturnKind
import com.example.collabpro.features.billing.domain.model.*
import com.example.collabpro.features.billing.infrastructure.browser.PayPalCustomTabs
import com.example.collabpro.features.identity.application.auth.SessionState
import com.example.collabpro.features.identity.domain.model.AccountType
import java.math.BigDecimal
import java.util.UUID

@Composable
internal fun BillingWorkspace(
    session: SessionState.Authenticated,
    model: SandboxBillingViewModel,
    destination: BillingDestination,
    onBack: () -> Unit,
    onMethods: () -> Unit,
    onSubscription: () -> Unit,
    onIncidents: (UUID) -> Unit
) {
    val observed by model.ui.collectAsStateWithLifecycle()
    val state = observed.takeIf {
        it.owner == session.account.accountId && it.expiresAt == session.expiresAt && it.destination == destination
    } ?: BillingState(loading = true)
    val brand = session.account.accountType == AccountType.BRAND
    val activity = LocalContext.current.findActivity()
    val approval: (PayPalReturnKind, UUID, String) -> Unit = { kind, id, url ->
        model.approveInBrowser(kind, id, url) { link -> activity?.let { PayPalCustomTabs.open(it, link) } == true }
    }
    LaunchedEffect(destination, session.account.accountId, session.expiresAt) { model.open(destination) }

    Page(title = when (destination.tab) {
        BillingTab.METHODS -> if (brand) "Medios de pago" else "Cuenta receptora"
        BillingTab.SUBSCRIPTION -> "Planes y suscripción"
        BillingTab.COMPENSATION -> "Compensación"
    }, eyebrow = "PAYPAL SANDBOX", onBack = onBack) {
        item { Notice("Entorno de pruebas. Usa cuentas de PayPal Sandbox.") }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.failure?.let { error -> item { Notice(error.code?.billingReason() ?: error.message) } }
        state.notice?.let { item { Notice(it) } }
        if (state.recovering) item {
            Panel("Operación sin confirmar") {
                Text("Recupera el resultado del intento original antes de iniciar otro.")
                Action("Recuperar operación", model::retryPending, enabled = !state.loading)
            }
        }
        val enabled = !state.loading && !state.recovering
        when (destination.tab) {
            BillingTab.METHODS -> methods(state, brand, enabled, model, approval, onSubscription)
            BillingTab.SUBSCRIPTION -> if (brand) item {
                SubscriptionContent(state, enabled, model, approval, onMethods)
            }
            BillingTab.COMPENSATION -> {
                state.compensation?.let { record ->
                    item(key = record.collaborationId) {
                        CompensationContent(record, state.usable, brand, enabled, model, approval, onMethods, onIncidents)
                    }
                }
                state.audit?.let { history ->
                    item { Text("Actividad de la compensación", style = MaterialTheme.typography.titleMedium) }
                    if (history.items.isEmpty()) item { Text("Aún no hay actividad registrada.") }
                    items(history.items, key = { it.id }) { entry ->
                        Panel(entry.type.billingStatus()) {
                            InfoRow("Fecha", entry.time.displayTime())
                            InfoRow("Origen", when (entry.actor) {
                                null -> "Sistema"
                                session.account.accountId -> "Tu cuenta"
                                else -> "Contraparte"
                            })
                        }
                    }
                    item { BillingPagination(history, !state.loading, model::compensationPage) }
                }
            }
        }
        item { Action("Actualizar estado", model::refresh, secondary = true, enabled = !state.loading) }
    }
}

private fun LazyListScope.methods(
    state: BillingState, brand: Boolean, enabled: Boolean, model: SandboxBillingViewModel,
    approval: (PayPalReturnKind, UUID, String) -> Unit, onSubscription: () -> Unit
) {
    state.authorization?.let { record ->
        item(key = record.authorizationId) {
            Panel("Vinculación PayPal") {
                AuthorizationFacts(record)
                if (record.status == "PENDING_APPROVAL") record.approvalUrl?.let { url ->
                    Action("Abrir PayPal Sandbox", { approval(PayPalReturnKind.LINK, record.authorizationId, url) }, enabled = enabled)
                }
                if (!record.terminal) ConfirmAction("Cancelar vinculación", "Se cancelará este intento de vinculación.",
                    enabled, secondary = true) { model.cancelLink(record) }
            }
        }
    }
    state.methods?.let { page ->
        if (page.items.isEmpty()) item {
            Notice(if (page.total == 0L) {
                if (brand) "Aún no tienes un medio de pago vinculado." else "Aún no tienes una cuenta receptora vinculada."
            } else "No hay medios en esta página.")
        }
        items(page.items, key = { it.methodId }) { method ->
            Panel(method.displayName()) {
                MethodFacts(method)
                if (method.status == "ACTIVE")
                    Action("Verificar medio", { model.validateMethod(method) }, secondary = true, enabled = enabled)
            }
        }
        item { BillingPagination(page, !state.loading, model::methodsPage) }
    }
    item {
        Action("Vincular PayPal Sandbox", model::beginLink, enabled = enabled && state.methods != null && state.authorization?.terminal != false)
        if (brand) Action("Planes y suscripción", onSubscription, secondary = true)
    }
}

@Composable
private fun SubscriptionContent(
    state: BillingState, enabled: Boolean, model: SandboxBillingViewModel,
    approval: (PayPalReturnKind, UUID, String) -> Unit, onMethods: () -> Unit
) {
    val current = state.subscription
    current?.let { record ->
        Panel("Suscripción actual") {
            SubscriptionFacts(record)
            val id = record.subscriptionId
            if (id != null) {
                if (record.status == "APPROVAL_PENDING") record.approvalUrl?.let { url ->
                    Action("Abrir PayPal Sandbox", { approval(PayPalReturnKind.SUBSCRIPTION, id, url) }, enabled = enabled)
                }
                if (record.status in setOf("APPROVAL_PENDING", "CANCEL_PENDING") && record.consentExpiresAt != null) {
                    Action("Recuperar aprobación", { model.resumeSubscription(record) }, enabled = enabled, secondary = true)
                }
                Action("Consultar suscripción", { model.refreshSubscription(record) }, enabled = !state.loading, secondary = true)
                if (!record.canStart && record.status != "CANCEL_PENDING")
                    ConfirmAction("Cancelar suscripción", "El cambio se verificará con PayPal Sandbox.",
                        enabled, secondary = true) { model.cancelSubscription(record) }
            }
        }
    }
    val selectedMethod = SelectPaymentMethod(state.usable, enabled && current?.canStart == true, onMethods,
        visible = current?.canStart == true && state.plans?.any { it.available } == true)
    state.plans?.let { plans ->
        Text("Planes de empresa", style = MaterialTheme.typography.titleMedium)
        if (plans.isEmpty()) Text("No hay planes disponibles.")
        plans.forEach { plan ->
            key(plan.planId) {
                Panel(plan.name) {
                    PlanFacts(plan)
                    if (current?.canStart == true && plan.available)
                        ConfirmAction("Elegir ${plan.name}", "Se solicitará la aprobación del plan en PayPal Sandbox.",
                            enabled && selectedMethod != null) { model.subscribe(plan, selectedMethod) }
                }
            }
        }
    }
    state.subscriptions?.let { page ->
        Panel("Historial de suscripciones") {
            if (page.items.isEmpty()) Text("Aún no hay suscripciones en el historial.")
            page.items.forEach { row ->
                row.subscriptionId?.let { id ->
                    Action("${row.planName ?: "Suscripción"} · ${row.status.billingStatus()}",
                        { model.inspectSubscription(id) }, secondary = true, enabled = !state.loading)
                }
            }
            BillingPagination(page, !state.loading, model::subscriptionsPage)
        }
    }
    state.historicalSubscription?.let { record ->
        AlertDialog(onDismissRequest = model::closeSubscriptionDetail, title = { Text("Detalle de suscripción") },
            text = { androidx.compose.foundation.layout.Column { SubscriptionFacts(record) } },
            confirmButton = { TextButton(onClick = model::closeSubscriptionDetail) { Text("Cerrar") } })
    }
}

@Composable
internal fun SubscriptionFacts(record: BillingSubscription) {
    InfoRow("Estado", record.status.billingStatus())
    record.planName?.let { InfoRow("Plan", it) }
    record.amount?.let { InfoRow("Importe", it.billingAmount(record.currency)) }
    if (record.status != "NONE") InfoRow("Beneficios", if (record.benefitsActive) "Activos" else "Inactivos")
    record.paidThrough?.let { InfoRow("Pagado hasta", it.displayTime()) }
    if (record.benefitsActive) record.benefits.forEach { Text("• $it") }
    if (record.status == "APPROVAL_PENDING" || record.status == "CANCEL_PENDING")
        record.consentExpiresAt?.let { InfoRow("Vence la aprobación", it.displayTime()) }
    record.lastCheckedAt?.let { InfoRow("Última consulta", it.displayTime()) }
    record.errorCode?.let { Notice(it.billingReason() ?: it.billingStatus()) }
}

@Composable
private fun CompensationContent(
    record: BillingCompensation, methods: List<BillingMethod>, brand: Boolean, enabled: Boolean,
    model: SandboxBillingViewModel, approval: (PayPalReturnKind, UUID, String) -> Unit,
    onMethods: () -> Unit, onIncidents: (UUID) -> Unit
) {
    CompensationFacts(record) { onIncidents(record.collaborationId) }

    if (!brand || !record.paymentApplicable || !record.eligible || record.wasPaid ||
        record.openIncidentCount > 0 || record.unavailableReason != null) return
    if ("INITIATE_PAYMENT" in record.availableActions) {
        if (record.fundedAt == null) {
            val method = SelectPaymentMethod(methods, enabled, onMethods)
            ConfirmAction("Iniciar pago Sandbox", "PayPal solicitará la aprobación del importe acordado.",
                enabled && method != null) { model.beginPayment(record, method) }
        } else {
            ConfirmAction("Reintentar envío Sandbox", "El importe ya ingresó. Se reintentará el envío al creador.",
                enabled) { model.beginPayment(record, null) }
        }
    }
    if ("CONFIRM_FUNDING" in record.availableActions && record.fundedAt == null) {
        record.approvalUrl?.let { url ->
            Action("Abrir aprobación en PayPal", { approval(PayPalReturnKind.COMPENSATION, record.collaborationId, url) }, enabled = enabled)
        }
        ConfirmAction("Confirmar ingreso Sandbox", "Confirma después de aprobar la orden en PayPal. El envío al creador se verifica por separado.",
            enabled) { model.confirmPayment(record) }
    }
}

@Composable
internal fun CompensationFacts(record: BillingCompensation, onIncidents: (() -> Unit)? = null) {
    Panel("Condiciones acordadas") {
        InfoRow("Tipo", record.type.billingStatus())
        record.amount?.let { InfoRow("Importe", it.billingAmount(record.currency)) }
        record.description?.let { Text(it) }
        InfoRow("Estado", record.status.billingStatus())
        if (record.paymentApplicable) {
            InfoRow("Ingreso de empresa", record.fundedAt?.displayTime() ?: record.fundingStatus?.billingStatus() ?: "Sin captura confirmada")
            InfoRow("Envío al creador", record.paidAt?.displayTime() ?: record.payoutStatus?.billingStatus() ?: "Sin envío confirmado")
            if (record.status == "PAYMENT_REVERSED") Notice("El proveedor informó una reversión.")
            else if (record.wasPaid) Notice("Envío al creador confirmado.")
        }
        if (record.openIncidentCount > 0) {
            InfoRow("Incidencias abiertas", record.openIncidentCount.toString())
            if (onIncidents != null) Action("Ver incidencias", onIncidents, secondary = true)
        }
        record.unavailableReason?.let { Notice(it.billingReason() ?: it.billingStatus()) }
        record.errorCode?.takeUnless { it == record.unavailableReason }?.let { Notice(it.billingReason() ?: it.billingStatus()) }
    }
}

@Composable
internal fun AuthorizationFacts(record: BillingAuthorization) {
    InfoRow("Estado", record.status.billingStatus())
    if (!record.terminal) InfoRow("Vence", record.expiresAt.displayTime())
    record.errorCode?.let { Notice(it.billingReason() ?: it.billingStatus()) }
}

@Composable
internal fun MethodFacts(method: BillingMethod) {
    InfoRow("Estado", method.status.billingStatus())
    InfoRow("Propósito", if (method.purpose == BillingPurpose.PAYMENT) "Pagar" else "Recibir compensaciones")
    InfoRow("Vinculado", method.linkedAt.displayTime())
}

@Composable
internal fun PlanFacts(plan: SubscriptionPlan) {
    Text(plan.description)
    val interval = if (plan.interval == "MONTH") "al mes" else "· ${plan.interval}"
    InfoRow("Precio", "${plan.amount.billingAmount(plan.currency)} $interval")
    if (plan.demonstration) Text("Plan de demostración")
    plan.benefits.forEach { Text("• $it") }
    if (!plan.available) Notice(plan.errorCode?.billingReason() ?: "Plan temporalmente no disponible.")
}

@Composable
private fun SelectPaymentMethod(methods: List<BillingMethod>, enabled: Boolean, onMethods: () -> Unit, visible: Boolean = true): BillingMethod? {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    if (!visible) return null
    val selected = methods.singleOrNull() ?: methods.find { it.methodId.toString() == selectedId }
    Panel("Medio de pago de empresa") {
        if (selected != null) InfoRow("Seleccionado", selected.displayName())
        if (methods.isEmpty()) {
            Notice("Vincula un medio de pago de empresa para continuar.")
            Action("Ir a medios de pago", onMethods, secondary = true, enabled = enabled)
        } else if (methods.size > 1) {
            methods.forEach { method ->
                Action(method.displayName(), { selectedId = method.methodId.toString() },
                    secondary = method != selected, enabled = enabled && method != selected)
            }
        }
    }
    return selected
}

@Composable
private fun ConfirmAction(label: String, body: String, enabled: Boolean, secondary: Boolean = false, action: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Action(label, { confirm = true }, secondary = secondary, enabled = enabled)
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(label) }, text = { Text(body) },
        confirmButton = { TextButton(enabled = enabled, onClick = { confirm = false; action() }) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Volver") } })
}

@Composable
private fun BillingPagination(page: ResultPage<*>, enabled: Boolean, onPage: (Int) -> Unit) {
    if (page.total <= page.size) return
    Text("Página ${page.page + 1} · ${page.total} registros")
    if (page.page > 0)
        Action("Página anterior", { onPage(page.page - 1) }, secondary = true, enabled = enabled)
    if ((page.page.toLong() + 1) * page.size < page.total)
        Action("Página siguiente", { onPage(page.page + 1) }, secondary = true, enabled = enabled)
}

private fun BillingMethod.displayName() = maskedLabel?.takeIf(String::isNotBlank) ?: "PayPal · ${methodId.toString().takeLast(6)}"

private fun BigDecimal.billingAmount(currency: String?) = listOfNotNull(currency, toPlainString()).joinToString(" ")

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
