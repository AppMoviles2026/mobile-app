package com.example.collabpro.features.campaign.presentation.applicants

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.features.campaign.domain.model.ApplicantDecision
import com.example.collabpro.features.campaign.domain.model.ApplicationStatus
import com.example.collabpro.features.campaign.presentation.applications.label
import java.util.UUID

@Composable
fun EvaluationListScreen(state: ApplicantEvaluationState, onPage: (Int) -> Unit,
    onFilter: (ApplicationStatus?) -> Unit, onOpen: (UUID) -> Unit, onBack: () -> Unit) {
    var showFilters by remember { mutableStateOf(false) }
    Page("Postulaciones", subtitle = state.campaign?.summary?.title.orEmpty(), onBack = onBack) {
        item {
            TextButton({ showFilters = true }, enabled = !state.busy) { Text(state.filter?.label() ?: "Todos los estados") }
            DropdownMenu(showFilters, { showFilters = false }) {
                DropdownMenuItem(text = { Text("Todos los estados") }, onClick = { showFilters = false; onFilter(null) })
                ApplicationStatus.entries.forEach { status ->
                    DropdownMenuItem(text = { Text(status.label()) }, onClick = { showFilters = false; onFilter(status) })
                }
            }
        }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.failure?.let { item { Notice(it.message) } }
        if (state.page?.items?.isEmpty() == true && state.failure == null) item { Notice("No hay postulaciones para este filtro.") }
        items(state.page?.items.orEmpty(), key = { it.application.id }) { entry ->
            LinkCard(entry.creator?.name ?: "Perfil no disponible", entry.application.message,
                { if (!state.busy) onOpen(entry.application.id) }, entry.application.status.label())
        }
        item { Action("Actualizar postulaciones", { onPage(state.page?.page ?: 0) }, secondary = true, enabled = !state.busy) }
        state.page?.let { page -> item {
            Text("Página ${page.page + 1} · ${page.total} postulaciones")
            if (page.page > 0) TextButton({ onPage(page.page - 1) }, enabled = !state.busy) { Text("Anterior") }
            if ((page.page.toLong() + 1) * page.size < page.total)
                TextButton({ onPage(page.page + 1) }, enabled = !state.busy) { Text("Siguiente") }
        } }
    }
}

@Composable
fun EvaluationDetailScreen(state: ApplicantEvaluationState, onRefresh: () -> Unit,
    onDecide: (ApplicantDecision, String) -> Unit, onBack: () -> Unit, onAgreement: (UUID) -> Unit = {}) {
    val entry = state.candidate
    val application = entry?.application
    var decision by remember(application?.id) { mutableStateOf<ApplicantDecision?>(null) }
    var reason by remember(application?.id) { mutableStateOf(state.reasonDraft) }
    var reasonFor by remember(application?.id) { mutableStateOf(state.decisionDraft) }
    fun openDecision(choice: ApplicantDecision) {
        if (reasonFor != choice) reason = ""
        reasonFor = choice
        decision = choice
    }
    decision?.let { choice ->
        val rejecting = choice == ApplicantDecision.REJECTED
        AlertDialog(
            onDismissRequest = { decision = null },
            title = { Text(if (rejecting) "Rechazar postulación" else "Seleccionar postulante") },
            text = {
                OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(),
                    label = { Text(if (rejecting) "Motivo del rechazo" else "Nota opcional") },
                    supportingText = { Text("${reason.length}/2000") })
            },
            confirmButton = { TextButton({
                decision = null
                onDecide(choice, reason)
            }, enabled = reason.trim().length <= 2000 && (!rejecting || reason.isNotBlank())) { Text("Confirmar") } },
            dismissButton = { TextButton({ decision = null }) { Text("Volver") } }
        )
    }
    Page("Detalle de postulación", onBack = onBack) {
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.failure?.let { item { Notice(it.message) } }
        if (entry != null && application != null) {
            item { Panel(entry.creator?.name ?: "Perfil no disponible") {
                Status(application.status.label())
                entry.creator?.let { creator ->
                    if (!creator.active) Text("Perfil inactivo")
                    creator.biography?.let { Text(it) }
                    creator.niche?.let { InfoRow("Nicho", it) }
                    creator.audienceDescription?.let { InfoRow("Audiencia", it) }
                    creator.location?.let { InfoRow("Ubicación", it) }
                    creator.socialAccounts.forEach { network -> InfoRow(network.platform, network.username) }
                }
            } }
            item { Panel("Propuesta") {
                Text(application.message)
                InfoRow("Enviada", application.submittedAt.displayTime())
                application.decidedAt?.let { InfoRow("Decisión", it.displayTime()) }
                application.decisionReason?.let { InfoRow("Motivo", it) }
            } }
            state.campaign?.requirements?.takeIf { it.isNotEmpty() }?.let { requirements -> item {
                Panel("Requisitos de la campaña") {
                    requirements.forEach { requirement ->
                        val status = when {
                            requirement.id in application.confirmedRequirementIds -> "Confirmación del creador"
                            requirement.ruleType == com.example.collabpro.features.campaign.domain.model.RequirementRule.MANUAL_CONFIRMATION -> "Sin confirmación registrada"
                            else -> if (requirement.mandatory) "Obligatorio" else "Opcional"
                        }
                        InfoRow(requirement.description, status)
                    }
                }
            } }
            if (application.status == ApplicationStatus.SELECTED)
                item { Action("Proponer o consultar acuerdo", { onAgreement(application.id) }, enabled = !state.busy && !state.mustRefresh) }
            if (application.status == ApplicationStatus.PENDING) {
                val enabled = !state.busy && !state.mustRefresh
                item { Action("Seleccionar postulante", { openDecision(ApplicantDecision.SELECTED) }, enabled = enabled) }
                item { Action("Rechazar postulación", { openDecision(ApplicantDecision.REJECTED) }, secondary = true, enabled = enabled) }
            }
        }
        item { Action("Actualizar detalle", onRefresh, secondary = true, enabled = !state.busy && state.applicationId != null) }
    }
}
