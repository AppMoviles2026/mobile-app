package com.example.collabpro.features.campaign.presentation.manage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.drafts.*
import com.example.collabpro.features.campaign.domain.model.*
import java.util.UUID

@Composable
fun OwnCampaignsScreen(state: OwnCampaignsUiState, editor: CampaignEditorUiState,
    onPrepare: () -> Unit = {}, onNew: () -> Unit = {}, onOpen: (UUID) -> Unit = {}, onPage: (Int) -> Unit = {}, onBack: () -> Unit = {}) {
    var confirmNew by remember { mutableStateOf(false) }
    if (confirmNew) Confirmation("¿Comenzar otra preparación?", "La edición local actual se reemplazará. Un borrador ya guardado permanece en el servidor; una creación sin confirmar debe resolverse primero.",
        { confirmNew = false; onNew() }, { confirmNew = false })
    Page("Mis campañas", subtitle = "Borradores y campañas de tu empresa, consultados en el servidor.", onBack = onBack) {
        item { Action("Crear o continuar campaña", onPrepare, enabled = !editor.restoring && !editor.busy && editor.draft != null) }
        item { Action(if (editor.draft == null) "Restablecer preparación local" else "Comenzar otra campaña", { confirmNew = true }, secondary = true, enabled = !editor.busy && !editor.restoring) }
        if (editor.localSaving) item { Text("Guardando edición local cifrada…") }
        editor.storageFailure?.let { item { CampaignError(it) } }
        editor.failure?.let { item { CampaignError(it) } }
        if (editor.draft?.serverId != null) item { Notice("Tienes una preparación guardada. «Crear o continuar campaña» retoma su UUID, no crea un duplicado.") }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.failure?.let { item { CampaignError(it) } }; state.notice?.let { item { Notice(it) } }
        if (state.page?.items?.isEmpty() == true) item { Notice("No hay campañas en esta página.") }
        if (state.failure != null && state.page != null) item { Text("La lista conserva la última consulta correcta y puede estar desactualizada.") }
        items(state.page?.items.orEmpty(), key = { it.id.toString() }) { campaign ->
            LinkCard(campaign.title, "${campaign.category} • ${campaign.location ?: "Sin ubicación"}", { onOpen(campaign.id) }, campaign.status.label())
        }
        item { Action("Actualizar lista", { onPage(state.page?.page ?: 0) }, secondary = true, enabled = !state.loading) }
        state.page?.let { page -> item {
            Text("Página ${page.page + 1} • ${page.total} campañas en total")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ onPage(page.page - 1) }, enabled = page.page > 0 && !state.loading) { Text("Anterior") }
                TextButton({ onPage(page.page + 1) }, enabled = (page.page.toLong() + 1) * page.size < page.total && !state.loading) { Text("Siguiente") }
            }
        } }
    }
}

@Composable
fun CampaignBasicsScreen(state: CampaignEditorUiState, onEdit: (CampaignBasics.() -> CampaignBasics) -> Unit = {},
    onContinue: () -> Unit = {}, onSave: () -> Unit = {}, onCheck: () -> Unit = {}, onBack: () -> Unit = {}) {
    val draft = state.draft
    val enabled = !state.metadataLocked && !state.busy && !state.restoring
    Page("Preparar campaña", eyebrow = "PASO 1 DE 2", subtitle = "Continuar solo cambia de paso. Guardar borrador lo crea en el servidor.", onBack = onBack) {
        item { EditorFeedback(state) }
        if (state.metadataLocked) item { Notice("Los metadatos están bloqueados porque la campaña existe o su creación aún no está confirmada. Puedes completar condiciones; no hay edición de metadatos en esta versión.") }
        draft?.basics?.let { body ->
            item { CampaignField("Título", body.title, "title", state.failure, enabled, 200) { value -> onEdit { copy(title = value) } } }
            item { CampaignField("Objetivo", body.objective, "objective", state.failure, enabled, 2000, true) { value -> onEdit { copy(objective = value) } } }
            item { CampaignField("Descripción (opcional)", body.description, "description", state.failure, enabled, 5000, true) { value -> onEdit { copy(description = value) } } }
            item { CampaignField("Categoría", body.category, "category", state.failure, enabled, 100) { value -> onEdit { copy(category = value) } } }
            item { CampaignField("Público objetivo", body.targetAudience, "targetAudience", state.failure, enabled, 2000, true) { value -> onEdit { copy(targetAudience = value) } } }
            item { CampaignField("Ubicación (opcional)", body.location, "location", state.failure, enabled, 150) { value -> onEdit { copy(location = value) } } }
            item { Action("Continuar a condiciones", onContinue, enabled = !state.busy && !state.restoring) }
            item { Action(if (state.busy) "Procesando…" else "Guardar borrador", onSave, secondary = true, enabled = !state.busy && !state.readOnly && !state.restoring) }
            if (draft.serverId != null) item { Action("Comprobar estado en el servidor", onCheck, secondary = true, enabled = !state.busy) }
        }
    }
}

@Composable
fun CampaignConditionsScreen(state: CampaignEditorUiState, onEdit: (ConditionsDraft.() -> ConditionsDraft) -> Unit = {},
    onAddRequirement: () -> Unit = {}, onAddDeliverable: () -> Unit = {}, onSave: () -> Unit = {}, onPublish: () -> Unit = {},
    onCheck: () -> Unit = {}, onReload: () -> Unit = {}, onList: () -> Unit = {}, onBack: () -> Unit = {}) {
    var confirmReload by remember { mutableStateOf(false) }
    var confirmPublish by remember { mutableStateOf(false) }
    if (confirmReload) Confirmation("¿Reemplazar la edición local?", "Se cargarán las condiciones guardadas en el servidor. Los cambios locales sin guardar se perderán.",
        { confirmReload = false; onReload() }, { confirmReload = false })
    if (confirmPublish) Confirmation("¿Publicar esta campaña?", "Se guardarán las condiciones válidas y la campaña será visible para creadores. Después ya no se podrán editar sus condiciones en esta versión.",
        { confirmPublish = false; onPublish() }, { confirmPublish = false })
    val terms = state.draft?.conditions
    val enabled = !state.conditionsLocked
    Page("Condiciones de campaña", eyebrow = "PASO 2 DE 2", subtitle = "Define requisitos, entregables, plazos y oferta. La compensación aquí no inicia un pago.", onBack = onBack) {
        item { EditorFeedback(state) }
        if (state.readOnly) item { Notice("Campaña ${state.details?.summary?.status?.label()}. Sus condiciones son de solo lectura.") }
        if (terms != null) {
            item { Notice("Fechas en ${terms.zoneId}. Formato: aaaa-mm-dd hh:mm, hora de 24 horas. Cada entrega debe ocurrir después del cierre de postulaciones.") }
            item { CampaignField("Cierre de postulaciones", terms.applicationDeadline, "applicationDeadline", state.failure, enabled) { value -> onEdit { copy(applicationDeadline = value) } } }
            terms.requirements.forEachIndexed { index, row -> item(key = "r-${row.localId}") { Panel("Requisito ${index + 1}") {
                CampaignField("Descripción", row.description, "requirements[$index].description", state.failure, enabled, 2000, true) { value -> onEdit { copy(requirements = requirements.map { if (it.localId == row.localId) it.copy(description = value) else it }) } }
                Row { Text("Obligatorio", Modifier.weight(1f)); Switch(row.mandatory, { value -> onEdit { copy(requirements = requirements.map { if (it.localId == row.localId) it.copy(mandatory = value) else it }) } }, enabled = enabled) }
                CampaignSelection("Regla", row.rule, RequirementRule.entries, { it.label() }, enabled) { value -> onEdit { copy(requirements = requirements.map { if (it.localId == row.localId) it.copy(rule = value, expectedValue = "") else it }) } }
                if (row.rule == RequirementRule.AUTHORIZED_PLATFORM) CampaignSelection("Red autorizada", row.expectedValue, listOf("instagram", "tiktok"), { it }, enabled) { value -> onEdit { copy(requirements = requirements.map { if (it.localId == row.localId) it.copy(expectedValue = value) else it }) } }
                else if (row.rule != RequirementRule.MANUAL_CONFIRMATION) CampaignField("Valor requerido", row.expectedValue, "requirements[$index].expectedValue", state.failure, enabled, 150) { value -> onEdit { copy(requirements = requirements.map { if (it.localId == row.localId) it.copy(expectedValue = value) else it }) } }
                if (row.rule == RequirementRule.AUTHORIZED_PLATFORM) state.failure?.fieldErrors?.get("requirements[$index].expectedValue")?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Action("Quitar requisito", { onEdit { copy(requirements = requirements.filterNot { it.localId == row.localId }) } }, secondary = true, enabled = enabled && terms.requirements.size > 1)
            } } }
            item { Action("Añadir requisito (${terms.requirements.size}/50)", onAddRequirement, secondary = true, enabled = enabled && terms.requirements.size < 50) }
            terms.deliverables.forEachIndexed { index, row -> item(key = "d-${row.localId}") { Panel("Entregable ${index + 1}") {
                CampaignField("Formato de contenido", row.contentType, "deliverables[$index].contentType", state.failure, enabled, 100) { value -> onEdit { copy(deliverables = deliverables.map { if (it.localId == row.localId) it.copy(contentType = value) else it }) } }
                CampaignField("Descripción de entrega", row.description, "deliverables[$index].description", state.failure, enabled, 2000, true) { value -> onEdit { copy(deliverables = deliverables.map { if (it.localId == row.localId) it.copy(description = value) else it }) } }
                CampaignField("Cantidad", row.quantity, "deliverables[$index].quantity", state.failure, enabled, keyboard = KeyboardType.Number) { value -> onEdit { copy(deliverables = deliverables.map { if (it.localId == row.localId) it.copy(quantity = value) else it }) } }
                CampaignField("Fecha y hora de entrega", row.deadline, "deliverables[$index].deadline", state.failure, enabled) { value -> onEdit { copy(deliverables = deliverables.map { if (it.localId == row.localId) it.copy(deadline = value) else it }) } }
                Action("Quitar entregable", { onEdit { copy(deliverables = deliverables.filterNot { it.localId == row.localId }) } }, secondary = true, enabled = enabled && terms.deliverables.size > 1)
            } } }
            item { Action("Añadir entregable (${terms.deliverables.size}/50)", onAddDeliverable, secondary = true, enabled = enabled && terms.deliverables.size < 50) }
            item { Panel("Compensación ofrecida") {
                CampaignSelection("Tipo", terms.compensationType, CompensationType.entries, { it.label() }, enabled) { value -> onEdit { copy(compensationType = value, amount = "", currency = "") } }
                if (terms.compensationType == CompensationType.CASH) {
                    CampaignField("Monto", terms.amount, "compensation.amount", state.failure, enabled, keyboard = KeyboardType.Decimal) { value -> onEdit { copy(amount = value) } }
                    CampaignField("Moneda (PEN, USD…)", terms.currency, "compensation.currency", state.failure, enabled, 3) { value -> onEdit { copy(currency = value) } }
                }
                CampaignField("Descripción de compensación", terms.compensationDescription, "compensation.description", state.failure, enabled, 2000, true) { value -> onEdit { copy(compensationDescription = value) } }
            } }
            if (!state.readOnly) {
                item { Action(if (state.busy) "Procesando…" else "Guardar condiciones", onSave, secondary = true, enabled = !state.busy && !state.restoring) }
                item { Action("Publicar campaña", { confirmPublish = true }, enabled = !state.busy && !state.restoring) }
            }
            if (state.draft.serverId != null) {
                item { Action("Comprobar estado en el servidor", onCheck, secondary = true, enabled = !state.busy) }
                item { Action("Recargar condiciones del servidor", { confirmReload = true }, secondary = true, enabled = !state.busy) }
            }
        }
        item { Action("Mis campañas", onList, secondary = true) }
    }
}

@Composable
fun OwnedCampaignDetailScreen(state: OwnedCampaignDetailUiState, onRetry: () -> Unit = {}, onEdit: () -> Unit = {},
    onDiscard: () -> Unit = {}, onClose: () -> Unit = {}, onBack: () -> Unit = {}) {
    var confirm by remember { mutableStateOf("") }
    if (confirm.isNotBlank()) Confirmation(if (confirm == "discard") "¿Eliminar este borrador?" else if (confirm == "edit") "¿Retomar este borrador?" else "¿Cerrar postulaciones?",
        if (confirm == "discard") "Se eliminará permanentemente del servidor. Solo está permitido para borradores."
        else if (confirm == "edit") "Si estabas preparando otra campaña, su edición local será reemplazada. Las campañas guardadas en el servidor se conservan."
        else "No se recibirán nuevas postulaciones. La campaña y sus postulaciones existentes se conservarán.",
        { val action = confirm; confirm = ""; if (action == "discard") onDiscard() else if (action == "edit") onEdit() else onClose() }, { confirm = "" })
    Page(state.details?.summary?.title ?: "Detalle de campaña", subtitle = "Información real de tu campaña.", onBack = onBack) {
        if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.failure?.let { item { CampaignError(it) } }; state.notice?.let { item { Notice(it) } }
        state.details?.let { details ->
            item { Status(details.summary.status.label()) }
            item { Panel("Información") { Text(details.objective); details.description?.let { Text(it) }; InfoRow("Categoría", details.summary.category); InfoRow("Audiencia", details.targetAudience); InfoRow("Ubicación", details.summary.location ?: "No indicada") } }
            item { Panel("Fechas y oferta") {
                Text("Cierre de postulaciones: ${details.summary.applicationDeadline ?: "Sin definir"}")
                Text("Publicación: ${details.publicationDate ?: "No publicada"}")
                details.summary.compensation?.let { Text(it.type.label()); Text(it.description); if (it.type == CompensationType.CASH) Text("${it.amount?.toPlainString()} ${it.currency}") } ?: Text("Compensación sin definir")
            } }
            items(details.requirements, key = { "r-${it.id}" }) { requirement -> Panel("Requisito ${if (requirement.mandatory) "obligatorio" else "opcional"}") { Text(requirement.description); Text(requirement.ruleType.label()); requirement.expectedValue?.let { Text(it) } } }
            items(details.deliverables, key = { "d-${it.id}" }) { deliverable -> Panel(deliverable.contentType) { Text(deliverable.description); Text("Cantidad: ${deliverable.quantity}"); Text("Entrega: ${deliverable.deadline}") } }
            if (details.summary.status == CampaignStatus.DRAFT) {
                item { Action("Retomar condiciones del borrador", { confirm = "edit" }, enabled = !state.busy && !state.loading) }
                item { Action("Eliminar borrador", { confirm = "discard" }, secondary = true, enabled = !state.busy && !state.loading) }
            } else if (details.summary.status == CampaignStatus.OPEN) item { Action("Cerrar postulaciones", { confirm = "close" }, secondary = true, enabled = !state.busy && !state.loading) }
        }
        item { Action("Actualizar detalle", onRetry, secondary = true, enabled = !state.busy && !state.loading) }
    }
}

@Composable private fun EditorFeedback(state: CampaignEditorUiState) {
    if (state.restoring || state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if (state.restoring) "Restaurando preparación…" else "Procesando con el servidor…") }
    if (state.localSaving) Text("Guardando edición local cifrada…")
    state.storageFailure?.let { CampaignError(it) }; state.failure?.let { CampaignError(it) }; state.notice?.let { Notice(it) }
    state.draft?.serverId?.let { Text("Campaña: $it", style = MaterialTheme.typography.labelSmall) }
    state.details?.let { Status(it.summary.status.label()) }
    if (state.draft?.pending != null) Notice("Hay una operación sin confirmar (${state.draft.pending.name}). Reintenta o comprueba el servidor; tus datos y el UUID se conservan.")
}
@Composable private fun CampaignField(label: String, value: String, field: String, failure: ApiFailure?, enabled: Boolean,
    max: Int? = null, multiline: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    val error = failure?.fieldErrors?.get(field)
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled, label = { Text(label) }, singleLine = !multiline,
        minLines = if (multiline) 3 else 1, isError = error != null, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        supportingText = { if (error != null) Text(error) else if (max != null) Text("${value.length}/$max") })
}
@Composable private fun <T> CampaignSelection(label: String, selected: T, options: List<T>, text: (T) -> String,
    enabled: Boolean, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton({ expanded = true }, enabled = enabled) { Text("$label: ${text(selected).ifBlank { "Seleccionar" }} ▾") }
        DropdownMenu(expanded, { expanded = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(text(option)) }, onClick = { expanded = false; onSelect(option) }) } }
    }
}
@Composable private fun Confirmation(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onConfirm) { Text("Confirmar") } }, dismissButton = { TextButton(onDismiss) { Text("Volver") } })
}
@Composable private fun CampaignError(failure: ApiFailure) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) { Text(failure.message, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) }
}
private fun CampaignStatus.label() = when (this) { CampaignStatus.DRAFT -> "Borrador"; CampaignStatus.OPEN -> "Publicada"; CampaignStatus.CLOSED -> "Cerrada"; CampaignStatus.CANCELLED -> "Cancelada" }
private fun CompensationType.label() = when (this) { CompensationType.CASH -> "Dinero"; CompensationType.PRODUCT -> "Producto"; CompensationType.SERVICE -> "Servicio"; CompensationType.CREDIT -> "Crédito"; CompensationType.BARTER -> "Canje" }
private fun RequirementRule.label() = when (this) { RequirementRule.MANUAL_CONFIRMATION -> "Confirmación manual"; RequirementRule.NICHE_EQUALS -> "Nicho del creador"; RequirementRule.LOCATION_EQUALS -> "Ubicación del creador"; RequirementRule.AUTHORIZED_PLATFORM -> "Red social autorizada" }
