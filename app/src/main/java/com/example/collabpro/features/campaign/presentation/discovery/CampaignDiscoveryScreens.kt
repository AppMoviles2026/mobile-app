package com.example.collabpro.features.campaign.presentation.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.discovery.DiscoveryFilters
import com.example.collabpro.features.campaign.domain.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun CampaignExploreScreen(state: CampaignDiscoveryUiState, onEdit: (DiscoveryFilters.() -> DiscoveryFilters) -> Unit = {},
    onSearch: () -> Unit = {}, onClear: () -> Unit = {}, onRetry: () -> Unit = {}, onRefresh: () -> Unit = {},
    onNext: () -> Unit = {}, onPrevious: () -> Unit = {}, onDetail: (UUID) -> Unit = {}, onBack: () -> Unit = {}) {
    val results = state.results
    Page("Explorar campañas", subtitle = "Oportunidades publicadas que admiten postulaciones. Los filtros se consultan en el servidor.", onBack = onBack) {
        item { Panel("Buscar oportunidades") {
            SearchField("Marca, título u objetivo", state.filters.query, "q", state.filterFailure) { value -> onEdit { copy(query = value) } }
            SearchField("Categoría", state.filters.category, "category", state.filterFailure) { value -> onEdit { copy(category = value) } }
            Text("La categoría debe coincidir con la registrada; no distingue mayúsculas.", style = MaterialTheme.typography.bodySmall)
            SearchField("Ubicación", state.filters.location, "location", state.filterFailure) { value -> onEdit { copy(location = value) } }
            CompensationFilter(state.filters.compensationType) { value -> onEdit { copy(compensationType = value) } }
            state.filterFailure?.let { DiscoveryError(it) }
            Action("Buscar campañas", onSearch, enabled = !results.loading)
            Action("Limpiar filtros", onClear, secondary = true)
        } }
        if (state.pendingFilters) item { Notice("Tienes filtros sin aplicar. Pulsa Buscar campañas; los resultados actuales corresponden a la consulta anterior.") }
        if (results.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Consultando campañas…") }
        results.failure?.let { error -> item { Panel("No se pudo consultar") { DiscoveryError(error); Action("Reintentar búsqueda", onRetry, enabled = !results.loading) } } }
        results.page?.let { page ->
            item { Text("${page.total} oportunidades · Página ${page.page.toLong() + 1} de ${maxOf(1L, (page.total + page.size - 1) / page.size)}") }
            if (!results.loading && results.failure == null && page.items.isEmpty()) item { Panel("Sin coincidencias") {
                Text(if (page.total == 0L) "No existen campañas disponibles para los criterios aplicados." else "No hay campañas en esta página. Regresa a la anterior o actualiza la consulta.")
                Action("Limpiar filtros", onClear, secondary = true)
            } }
            items(page.items, key = { it.id }) { summary -> CampaignOpportunity(summary, state.now, onDetail, stale = results.loading) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Anterior", onPrevious, secondary = true, enabled = !results.loading && !state.pendingFilters && results.hasPrevious, modifier = Modifier.weight(1f))
                Action("Siguiente", onNext, secondary = true, enabled = !results.loading && !state.pendingFilters && results.hasNext, modifier = Modifier.weight(1f))
            } }
        }
        item { Action("Actualizar resultados", onRefresh, secondary = true, enabled = !results.loading && !state.pendingFilters) }
    }
}

@Composable
fun CreatorCampaignDetailScreen(state: DiscoveryDetailUiState, now: Instant, onRefresh: () -> Unit = {}, onBack: () -> Unit = {}) {
    Page(state.details?.summary?.title ?: "Detalle de campaña", eyebrow = state.details?.summary?.brandName ?: "CAMPAÑA",
        subtitle = "Revisa las condiciones antes de decidir si puedes cumplirlas.", onBack = onBack) {
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Verificando condiciones y disponibilidad…") }
        state.failure?.let { error -> item { Panel(if (error.kind == FailureKind.NOT_FOUND) "Campaña no encontrada" else "No se pudo verificar la campaña") {
            DiscoveryError(error); Text("Actualiza para volver a consultar su información o regresa a la lista.")
        } } }
        state.details?.let { details ->
            val summary = details.summary
            item { Status(summary.status.text()); Notice(summary.availability(now).text()) }
            item { Panel("Objetivo y descripción") { Text(details.objective); details.description?.let { Text(it) }
                InfoRow("Empresa", summary.brandName); InfoRow("Categoría", summary.category)
                InfoRow("Público objetivo", details.targetAudience); InfoRow("Ubicación", summary.location ?: "No indicada")
            } }
            item { Panel("Fechas") { InfoRow("Publicación", details.publicationDate.dateText()); InfoRow("Cierre de postulaciones", summary.applicationDeadline.dateText()); Text("Zona horaria: ${ZoneId.systemDefault().id}", style = MaterialTheme.typography.bodySmall) } }
            item { Panel("Compensación ofrecida") { CompensationInfo(summary.compensation) } }
            item { Text("Requisitos", style = MaterialTheme.typography.titleLarge); Text("La disponibilidad de la campaña no significa que tu perfil ya haya sido aprobado. Las reglas se verificarán al postular.") }
            items(details.requirements, key = { "requirement-${it.id}" }) { requirement -> Panel(if (requirement.mandatory) "Requisito obligatorio" else "Requisito opcional") {
                Text(requirement.description); Text(requirement.ruleType.text()); requirement.expectedValue?.let { InfoRow("Valor requerido", it) }
            } }
            if (details.requirements.isEmpty()) item { Text("No se informaron requisitos.") }
            item { Text("Entregables", style = MaterialTheme.typography.titleLarge) }
            items(details.deliverables, key = { "deliverable-${it.id}" }) { deliverable -> Panel(deliverable.contentType) {
                Text(deliverable.description); InfoRow("Cantidad", deliverable.quantity.toString()); InfoRow("Fecha de entrega", deliverable.deadline.dateText())
            } }
            if (details.deliverables.isEmpty()) item { Text("No se informaron entregables.") }
            if (summary.availability(now) == CampaignAvailability.AVAILABLE) item { Notice("La campaña está disponible. El envío de postulaciones se conectará en la siguiente etapa; aquí no se simula un envío.") }
        }
        item { Action("Actualizar detalle", onRefresh, secondary = true, enabled = !state.loading && state.id != null) }
    }
}

@Composable
fun CreatorOpportunitiesPanel(state: DiscoveryPageUiState, now: Instant, onDetail: (UUID) -> Unit = {}, onRetry: () -> Unit = {}, onExplore: () -> Unit = {}) {
    Panel("Oportunidades recientes") {
        if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Consultando oportunidades…") }
        state.failure?.let { DiscoveryError(it); Action("Reintentar oportunidades", onRetry, secondary = true, enabled = !state.loading) }
        state.page?.let { page ->
            Text("${page.total} campañas disponibles en el servidor.")
            if (page.items.isEmpty() && !state.loading) Text("Todavía no hay campañas disponibles.")
            page.items.forEach { CampaignOpportunity(it, now, onDetail, stale = state.loading) }
        }
        Action("Ver todas las campañas", onExplore, secondary = true)
    }
}

@Composable private fun CampaignOpportunity(summary: CampaignSummary, now: Instant, onDetail: (UUID) -> Unit, stale: Boolean) {
    Panel(summary.title) {
        Text(summary.brandName); InfoRow("Categoría", summary.category); InfoRow("Ubicación", summary.location ?: "No indicada")
        CompensationInfo(summary.compensation); InfoRow("Postular hasta", summary.applicationDeadline.dateText())
        Status(if (stale) "Actualizando disponibilidad…" else summary.availability(now).text())
        Action("Ver condiciones", { onDetail(summary.id) }, secondary = true, enabled = !stale)
    }
}
@Composable private fun SearchField(label: String, value: String, field: String, error: ApiFailure?, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        isError = error?.fieldErrors?.containsKey(field) == true, supportingText = { error?.fieldErrors?.get(field)?.let { Text(it) } })
}
@Composable private fun CompensationFilter(value: CompensationType?, onSelect: (CompensationType?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton({ expanded = true }) { Text("Compensación: ${value?.text() ?: "Todas"} ▾") }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("Todas") }, onClick = { expanded = false; onSelect(null) })
            CompensationType.entries.forEach { option -> DropdownMenuItem(text = { Text(option.text()) }, onClick = { expanded = false; onSelect(option) }) }
        }
    }
}
@Composable private fun CompensationInfo(value: Compensation?) {
    if (value == null) Text("Compensación no informada") else {
        Text(value.type.text()); Text(value.description)
        if (value.type == CompensationType.CASH) Text("${value.amount?.toPlainString() ?: "Monto no informado"} ${value.currency.orEmpty()}")
    }
}
@Composable private fun DiscoveryError(error: ApiFailure) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) { Text(error.message, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) }
}
private fun Instant?.dateText(): String = this?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) ?: "No informada"
private fun CampaignStatus.text() = when (this) { CampaignStatus.DRAFT -> "Borrador"; CampaignStatus.OPEN -> "Publicada"; CampaignStatus.CLOSED -> "Cerrada"; CampaignStatus.CANCELLED -> "Cancelada" }
private fun CompensationType.text() = when (this) { CompensationType.CASH -> "Dinero"; CompensationType.PRODUCT -> "Producto"; CompensationType.SERVICE -> "Servicio"; CompensationType.CREDIT -> "Crédito"; CompensationType.BARTER -> "Canje" }
private fun RequirementRule.text() = when (this) { RequirementRule.MANUAL_CONFIRMATION -> "Confirmación manual al postular"; RequirementRule.NICHE_EQUALS -> "Nicho del perfil del creador"; RequirementRule.LOCATION_EQUALS -> "Ubicación del perfil del creador"; RequirementRule.AUTHORIZED_PLATFORM -> "Red social autorizada" }
private fun CampaignAvailability.text() = when (this) {
    CampaignAvailability.AVAILABLE -> "Admite nuevas postulaciones"
    CampaignAvailability.EXPIRED -> "El plazo venció: esta campaña no admite nuevas postulaciones."
    CampaignAvailability.CLOSED -> "Esta campaña está cerrada y no admite nuevas postulaciones."
    CampaignAvailability.CANCELLED -> "Esta campaña fue cancelada y no admite nuevas postulaciones."
    CampaignAvailability.UNPUBLISHED -> "Esta campaña no está publicada."
    CampaignAvailability.NOT_ACCEPTING -> "El servidor indica que esta campaña no admite nuevas postulaciones."
}
