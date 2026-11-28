package com.example.collabpro.features.campaign.presentation.applications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.domain.model.*
import java.time.Instant
import java.util.UUID

@Composable
fun OwnApplicationsScreen(state: OwnApplicationsListUiState, onDetail: (UUID) -> Unit = {}, onRetry: () -> Unit = {},
    onNext: () -> Unit = {}, onPrevious: () -> Unit = {}, onExplore: () -> Unit = {}, onBack: () -> Unit = {}) {
    Page("Mis postulaciones", subtitle = "Consulta el estado de tus propuestas.", onBack = onBack) {
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Consultando postulaciones…") }
        state.failure?.let { item { ApplicationError(it); Action("Reintentar consulta", onRetry, enabled = !state.loading) } }
        state.page?.let { page ->
            item { Text("${page.total} postulaciones · Página ${page.page.toLong() + 1} de ${maxOf(1L, (page.total + page.size - 1) / page.size)}") }
            if (page.items.isEmpty() && !state.loading) item { Panel("Sin postulaciones en esta página") {
                Text(if (page.total == 0L) "Todavía no has postulado a una campaña." else "La página no tiene resultados. Regresa a la anterior o actualiza.")
            } }
            items(page.items, key = { it.id }) { application -> Panel(application.campaignTitle) {
                Text(application.brandName); Status(application.status.label()); Text(application.message)
                InfoRow("Enviada", application.submittedAt.displayTime())
                Action("Consultar postulación", { onDetail(application.id) }, secondary = true, enabled = !state.loading)
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Anterior", onPrevious, secondary = true, enabled = state.hasPrevious && !state.loading, modifier = Modifier.weight(1f))
                Action("Siguiente", onNext, secondary = true, enabled = state.hasNext && !state.loading, modifier = Modifier.weight(1f))
            } }
        }
        item { Action("Actualizar postulaciones", onRetry, secondary = true, enabled = !state.loading) }
        item { Action("Explorar campañas", onExplore, secondary = true) }
    }
}

@Composable
fun OwnApplicationFormScreen(state: OwnApplicationFormUiState, now: Instant, onMessage: (String) -> Unit = {},
    onConfirm: (UUID, Boolean) -> Unit = { _, _ -> }, onSubmit: () -> Unit = {}, onSave: () -> Unit = {}, onVerify: () -> Unit = {},
    onResolve: (Boolean) -> Unit = {}, onList: () -> Unit = {}, onBack: () -> Unit = {}) {
    var resolution by remember(state.campaignId, state.latest) { mutableStateOf<Boolean?>(null) }
    resolution?.let { keep -> AlertDialog(onDismissRequest = { resolution = null }, title = { Text(if (keep) "¿Conservar tu propuesta?" else "¿Usar la propuesta guardada?") },
        text = { Text(if (keep) "Tu texto seguirá en el formulario. Revísalo y guarda cuando esté listo."
            else "Se reemplazará tu edición por la propuesta guardada.") },
        confirmButton = { TextButton({ resolution = null; onResolve(keep) }) { Text("Confirmar") } }, dismissButton = { TextButton({ resolution = null }) { Text("Volver") } }) }
    Page(if (state.application == null) "Postular a campaña" else "Mi propuesta", subtitle = "Solo una postulación por campaña. Las ediciones cambian únicamente el mensaje.", onBack = onBack) {
        if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if (state.busy) "Actualizando postulación…" else "Cargando postulación…") }
        state.failure?.let { item { ApplicationError(it) } }; state.notice?.let { item { Notice(it) } }
        item { Panel("Campaña") {
            Text(state.campaign?.summary?.title ?: state.application?.campaignTitle ?: "Pendiente de consulta")
            Text(state.campaign?.summary?.brandName ?: state.application?.brandName.orEmpty())
            state.campaign?.let { campaign -> Text(campaign.objective); InfoRow("Cierre de postulaciones", campaign.summary.applicationDeadline?.displayTime() ?: "No informado")
                Text(if (campaign.summary.availability(now) == CampaignAvailability.AVAILABLE) "Admite postulaciones nuevas." else "Esta campaña ya no admite postulaciones nuevas.") }
        } }
        state.application?.let { application -> item { Panel("Propuesta registrada") {
            Status(application.status.label()); Text(application.message)
            Text("Las confirmaciones del envío original no cambian al editar.", style = MaterialTheme.typography.bodySmall)
        } } }
        item { OutlinedTextField(state.proposal.message, onMessage, Modifier.fillMaxWidth(), enabled = !state.locked,
            label = { Text(if (state.application == null) "Tu propuesta" else "Tu propuesta") }, minLines = 4,
            isError = state.failure?.fieldErrors?.containsKey("message") == true, supportingText = {
                Text(state.failure?.fieldErrors?.get("message") ?: "${state.proposal.message.length}/4000")
            }) }
        if (state.application == null) {
            state.campaign?.requirements?.forEach { requirement -> item(key = "requirement-${requirement.id}") { Panel(if (requirement.mandatory) "Requisito obligatorio" else "Requisito opcional") {
                Text(requirement.description)
                if (requirement.ruleType == RequirementRule.MANUAL_CONFIRMATION) Row {
                    Checkbox(requirement.id in state.proposal.confirmations, { onConfirm(requirement.id, it) }, enabled = !state.locked)
                    Text("Confirmo que puedo cumplir este requisito.", Modifier.weight(1f))
                } else { Text("Se comprueba con tu perfil o tus redes vinculadas."); requirement.expectedValue?.let { InfoRow("Valor requerido", it) } }
                state.failure?.fieldErrors?.get("requirements.${requirement.id}")?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } } }
            state.failure?.fieldErrors?.filterKeys { it.startsWith("requirements.") && state.campaign?.requirements?.none { row -> "requirements.${row.id}" == it } != false }
                ?.forEach { (_, description) -> item { Notice("Requisito incumplido: $description") } }
            item { Action(if (state.submission != null) "Reintentar el mismo envío" else "Enviar postulación", onSubmit,
                enabled = !state.loading && !state.busy && state.checkedAbsence && state.campaign != null &&
                    (state.submission != null || state.campaign.summary.availability(now) == CampaignAvailability.AVAILABLE)) }
        } else {
            item { Panel("Confirmaciones originales") {
                if (state.application.confirmedRequirementIds.isEmpty()) Text("No hubo confirmaciones manuales.")
                val confirmations = state.application.confirmedRequirementIds
                val named = state.campaign?.requirements.orEmpty().filter { it.id in confirmations }
                named.forEach { Text(it.description) }
                if (named.size < confirmations.size) InfoRow("Total confirmado", confirmations.size.toString())
            } }
            if (!state.readOnly) item { Action("Guardar mensaje", onSave, enabled = !state.locked) }
            else item { Notice("Esta postulación ya no está pendiente. No se permite editarla ni volver a enviarla.") }
        }
        state.latest?.let { remote -> item { Panel("Propuesta actualizada") {
            Status(remote.status.label()); Text(remote.message)
            Text("Compara esta propuesta con tu edición antes de decidir cuál conservar.")
            Action("Usar propuesta guardada", { resolution = false }, secondary = true, enabled = !state.busy && !state.loading)
            if (remote.status == ApplicationStatus.PENDING) Action("Conservar mi propuesta para editar", { resolution = true }, secondary = true, enabled = !state.busy && !state.loading)
        } } }
        if (state.submission != null || state.pendingWrite != null) item { Notice("Comprueba el estado de la postulación antes de volver a enviarla o editarla.") }
        item { Action("Comprobar estado", onVerify, secondary = true, enabled = !state.busy && !state.loading && state.campaignId != null) }
        item { Action("Mis postulaciones", onList, secondary = true) }
    }
}

@Composable
fun OwnApplicationDetailScreen(state: OwnApplicationDetailUiState, onEdit: () -> Unit = {}, onCancel: () -> Unit = {},
    onVerify: () -> Unit = {}, onCampaign: (UUID) -> Unit = {}, onList: () -> Unit = {}, onBack: () -> Unit = {},
    onAgreement: (UUID) -> Unit = {}) {
    // A refresh must not let an already-open confirmation cancel a newly loaded version.
    var confirmCancel by remember(state.id, state.application?.version, state.application?.status) { mutableStateOf(false) }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false }, title = { Text("¿Cancelar esta postulación?") },
        text = { Text("Se conservará el registro como cancelado. Los cambios locales sin guardar no se enviarán. No podrás volver a postular a esta campaña.") },
        confirmButton = { TextButton({ confirmCancel = false; onCancel() }) { Text("Cancelar postulación") } }, dismissButton = { TextButton({ confirmCancel = false }) { Text("Volver") } })
    Page("Detalle de postulación", onBack = onBack) {
        if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Actualizando postulación…") }
        state.failure?.let { item { ApplicationError(it) } }; state.notice?.let { item { Notice(it) } }
        state.application?.let { application ->
            item { Panel(application.campaignTitle) {
                Text(application.brandName)
                Status(application.status.label())
                InfoRow("Enviada", application.submittedAt.displayTime())
                application.decidedAt?.let { InfoRow("Decisión", it.displayTime()) }
                application.decisionReason?.let { InfoRow("Motivo", it) }
            } }
            item { Panel("Propuesta enviada") { Text(application.message) } }
            item { Panel("Confirmaciones manuales originales") {
                if (application.confirmedRequirementIds.isEmpty()) Text("Sin confirmaciones manuales.")
                else InfoRow("Total confirmado", application.confirmedRequirementIds.size.toString())
            } }
            if (application.status == ApplicationStatus.PENDING) {
                item { Action("Editar propuesta", onEdit, enabled = !state.busy && !state.loading && !state.pendingOperation) }
                item { Action("Cancelar postulación", { confirmCancel = true }, secondary = true, enabled = !state.busy && !state.loading && !state.pendingOperation) }
            } else item { Notice("El estado actual es de solo lectura. No se permite una segunda postulación a esta misma campaña.") }
            if (application.status == ApplicationStatus.SELECTED) item {
                Action("Proponer o consultar acuerdo", { onAgreement(application.id) }, enabled = !state.busy && !state.loading)
            }
            item { Action("Ver condiciones de la campaña", { onCampaign(application.campaignId) }, secondary = true, enabled = !state.busy && !state.loading) }
        }
        if (state.pendingOperation) item { Notice("La última operación no está confirmada. Verifica antes de editar o cancelar de nuevo.") }
        item { Action("Actualizar o verificar postulación", onVerify, secondary = true, enabled = !state.loading && !state.busy && state.id != null) }
        item { Action("Mis postulaciones", onList, secondary = true) }
    }
}
@Composable private fun ApplicationError(error: ApiFailure) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) { Text(error.message, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) }
}
internal fun ApplicationStatus.label() = when (this) { ApplicationStatus.PENDING -> "Pendiente"; ApplicationStatus.SELECTED -> "Seleccionada"; ApplicationStatus.REJECTED -> "Rechazada"; ApplicationStatus.CANCELLED -> "Cancelada" }
