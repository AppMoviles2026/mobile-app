package com.example.collabpro.features.collaboration.presentation.workspace

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.features.collaboration.application.CollaborationInputs
import com.example.collabpro.features.collaboration.domain.model.*
import com.example.collabpro.features.collaboration.presentation.workspace.CollaborationPage.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Composable
internal fun <T> Selection(label: String, value: T, choices: List<T>, text: (T) -> String, enabled: Boolean, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }, enabled = enabled) { Text("$label · ${text(value)}") }
    DropdownMenu(expanded, { expanded = false }) {
        choices.forEach { choice ->
            DropdownMenuItem(text = { Text(text(choice)) }, onClick = { expanded = false; onSelect(choice) })
        }
    }
}

internal fun LazyListScope.agreement(record: AgreementRecord, enabled: Boolean, model: CollaborationWorkspaceViewModel) {
    item { Status(record.status.display()) }
    item { TermsPanel(record.snapshot) }
    item { SignaturesPanel(record) }
    item {
        var choice by remember(record.id, record.status, record.availableActions) { mutableStateOf<AgreementDecision?>(null) }
        choice?.let { selected ->
            ConfirmAction(
                if (selected == AgreementDecision.ACCEPT) "¿Aceptar estas condiciones?" else "¿Rechazar el acuerdo?",
                if (selected == AgreementDecision.ACCEPT) "La colaboración se activa cuando ambas partes aceptan." else "El acuerdo quedará cerrado.",
                onDismiss = { choice = null },
                onConfirm = { choice = null; model.decideAgreement(record, selected) }
            )
        }
        if ("ACCEPT_TERMS" in record.availableActions)
            Action("Aceptar condiciones", { choice = AgreementDecision.ACCEPT }, enabled = enabled)
        if ("REJECT_TERMS" in record.availableActions)
            Action("Rechazar condiciones", { choice = AgreementDecision.REJECT }, secondary = true, enabled = enabled)
        val collaborationId = record.collaborationId
        if (collaborationId != null)
            Action("Ver colaboración", { model.open(Destination(DETAIL, collaborationId)) }, enabled = enabled)
        else if (record.status == "ACCEPTED")
            Action("Recuperar colaboración", { model.recover(record) }, enabled = enabled)
        else if (record.status == "PENDING" && record.availableActions.isEmpty())
            Notice("El acuerdo está a la espera de la otra firma.")
    }
}

@Composable
internal fun TermsPanel(terms: TermsSnapshot) {
    Panel(terms.title) {
        InfoRow("Empresa", terms.brandName)
        InfoRow("Creador", terms.creatorName)
        InfoRow("Objetivo", terms.objective)
        terms.description?.let { InfoRow("Descripción", it) }
        InfoRow("Categoría", terms.category)
        InfoRow("Audiencia", terms.targetAudience)
        terms.location?.let { InfoRow("Ubicación", it) }
        InfoRow("Propuesta", terms.proposal)
        InfoRow("Cierre de postulaciones", terms.applicationDeadline.dateLabel())
        InfoRow("Límite para aceptar", terms.acceptanceDeadline.dateLabel())
        Text("Compensación acordada")
        val offer = terms.compensation
        InfoRow(offer.type.display(), listOfNotNull(offer.amount?.toPlainString(), offer.currency).joinToString(" ").ifBlank { offer.description })
        if (offer.amount != null) Text(offer.description)
        Text("Requisitos")
        terms.requirements.forEach { requirement ->
            Text(requirement.description)
            val required = if (requirement.mandatory) "Obligatorio" else "Opcional"
            val confirmed = if (requirement.id in terms.confirmedRequirements) " · Confirmado" else ""
            val rule = listOfNotNull(requirement.ruleType.display(), requirement.expectedValue).joinToString(" · ")
            Text("$required · $rule$confirmed", style = MaterialTheme.typography.bodySmall)
        }
        Text("Entregables")
        terms.deliverables.forEach {
            InfoRow("${it.quantity} × ${it.contentType}", it.deadline.dateLabel())
            Text(it.description)
        }
    }
}

@Composable
internal fun SignaturesPanel(record: AgreementRecord) {
    Panel("Firmas del acuerdo") {
        Status(record.status.display())
        InfoRow("Empresa", record.brandAcceptedAt?.dateLabel() ?: "Sin firma")
        InfoRow("Creador", record.creatorAcceptedAt?.dateLabel() ?: "Sin firma")
        record.decidedAt?.let { InfoRow("Decisión", it.dateLabel()) }
    }
}

internal fun LazyListScope.detail(record: CollaborationRecord, enabled: Boolean,
    model: CollaborationWorkspaceViewModel, historical: Boolean = false, onCompensation: (() -> Unit)? = null) {
    item {
        Panel(record.snapshot.title) {
            InfoRow("Empresa", record.snapshot.brandName)
            InfoRow("Creador", record.snapshot.creatorName)
            Status(record.status.name.display())
            InfoRow("Inicio", record.startedAt.dateLabel())
            record.nextDeadline?.let { InfoRow("Siguiente plazo", it.dateLabel()) }
            record.expiredAt?.let { InfoRow("Vencimiento", it.dateLabel()) }
            record.completedAt?.let { InfoRow("Finalización", it.dateLabel()) }
            record.cancelledAt?.let { InfoRow("Cancelación", it.dateLabel()) }
            InfoRow("Aprobadas", "${record.approvedQuantity} de ${record.requiredQuantity}")
            InfoRow("Presentadas", record.submittedQuantity.toString())
            InfoRow("Por revisar", record.pendingValidationQuantity.toString())
            InfoRow("Rechazadas", record.rejectedQuantity.toString())
            InfoRow("Incidencias abiertas", record.openIncidentCount.toString())
            record.incidentWindowClosesAt?.let { InfoRow("Plazo para abrir incidencias", it.dateLabel()) }
            record.pendingActions.forEach { Text(it.display()) }
        }
    }
    item {
        if (onCompensation != null) Action("Ver compensación", onCompensation, secondary = true, enabled = enabled)
        Action("Ver entregas", { model.open(Destination(DELIVERIES, record.id)) }, enabled = enabled)
        Action("Ver incidencias", { model.open(Destination(INCIDENTS, record.id)) }, secondary = true, enabled = enabled)
        Action("Ver cronología", { model.open(Destination(TIMELINE, record.id)) }, secondary = true, enabled = enabled)
        if (!historical)
            Action("Ver acuerdo firmado", { model.open(Destination(AGREEMENT, resourceId = record.agreementId)) }, secondary = true, enabled = enabled)
        if (record.canOpenIncident)
            Action("Registrar incidencia", { model.open(Destination(OPEN_INCIDENT, record.id)) }, secondary = true, enabled = enabled)
    }
    record.deliverables.forEach { obligation ->
        item(key = obligation.deliverableId) {
            Panel(obligation.contentType) {
                Text(obligation.description)
                InfoRow("Entregadas", "${obligation.submittedQuantity} de ${obligation.requiredQuantity}")
                InfoRow("Aprobadas", obligation.approvedQuantity.toString())
                InfoRow("Pendientes de revisión", obligation.pendingValidationQuantity.toString())
                InfoRow("Rechazadas", obligation.rejectedQuantity.toString())
                InfoRow("Fecha acordada", obligation.deadline.dateLabel())
                obligation.correctionDeadline?.let { InfoRow("Corrección", it.dateLabel()) }
                obligation.nextActionDeadline?.let { InfoRow("Siguiente plazo", it.dateLabel()) }
                if (obligation.overdue) Text("Plazo vencido")
                if ("SUBMIT_DELIVERABLE" in record.availableActions && obligation.submittedQuantity < obligation.requiredQuantity)
                    Action(if (obligation.overdue) "Presentar entrega tardía" else "Presentar pieza",
                        { model.open(Destination(SUBMIT, record.id, obligation.deliverableId)) }, enabled = enabled)
            }
        }
    }
}

internal fun LazyListScope.delivery(record: DeliveryRecord, enabled: Boolean, model: CollaborationWorkspaceViewModel,
    export: (UUID) -> Unit, openUrl: (String) -> Unit) {
    item {
        Panel("Pieza ${record.pieceNumber}") {
            Status(record.status.display())
            record.effectiveCorrectionDeadline?.let { InfoRow("Plazo vigente para corregir", it.dateLabel()) }
        }
    }
    item { SubmissionPanel(record.currentSubmission, record.currentReview, enabled, export, openUrl) }
    item {
        key(record.id, record.version, record.currentSubmission.id) {
            ReviewControls(record, enabled, model)
        }
        if ("SUBMIT_REVISION" in record.availableActions)
            Action("Enviar corrección", { model.open(Destination(CORRECT, record.collaborationId, record.id)) }, enabled = enabled)
        Action("Historial de versiones", { model.open(Destination(REVISIONS, record.collaborationId, record.id)) }, secondary = true, enabled = enabled)
        Action("Ver incidencias", { model.open(Destination(INCIDENTS, record.collaborationId)) }, secondary = true, enabled = enabled)
        Action("Registrar incidencia sobre esta pieza", { model.open(Destination(OPEN_INCIDENT, record.collaborationId, record.id)) }, secondary = true, enabled = enabled)
        Action("Estado de la colaboración", { model.open(Destination(DETAIL, record.collaborationId)) }, secondary = true, enabled = enabled)
    }
}

@Composable
private fun ReviewControls(record: DeliveryRecord, enabled: Boolean, model: CollaborationWorkspaceViewModel) {
    var approve by remember { mutableStateOf<Boolean?>(null) }
    var observation by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    approve?.let { approval ->
        val deadline = CollaborationInputs.deadline(date)
        AlertDialog(
            onDismissRequest = { approve = null },
            title = { Text(if (approval) "Aprobar versión ${record.currentSubmission.revision}" else "Rechazar versión ${record.currentSubmission.revision}") },
            text = { androidx.compose.foundation.layout.Column {
                Field(if (approval) "Observaciones opcionales" else "Observaciones", observation, enabled) { observation = it }
                if (!approval) DeadlineField(date, enabled) { date = it }
            } },
            confirmButton = { TextButton({
                approve = null
                model.review(record, approval, observation.trim().ifEmpty { null }, if (approval) null else deadline)
            }, enabled = enabled && observation.trim().length <= 4000 &&
                (approval || (observation.isNotBlank() && deadline?.isAfter(Instant.now()) == true))) { Text("Confirmar") } },
            dismissButton = { TextButton({ approve = null }) { Text("Volver") } }
        )
    }
    if ("APPROVE_DELIVERABLE" in record.availableActions)
        Action("Aprobar entrega", { approve = true }, enabled = enabled)
    if ("REJECT_DELIVERABLE" in record.availableActions)
        Action("Rechazar y fijar corrección", { approve = false }, secondary = true, enabled = enabled)
}

@Composable
internal fun SubmissionPanel(submission: Submission, review: Review?, enabled: Boolean, export: (UUID) -> Unit, openUrl: (String) -> Unit) {
    Panel("Versión ${submission.revision}") {
        InfoRow("Presentada", submission.submittedAt.dateLabel())
        if (submission.late) Status("Fuera del plazo original")
        ReferenceAction("Contenido", submission.content.content, enabled, export, openUrl)
        submission.content.evidence.forEachIndexed { index, reference ->
            ReferenceAction("Evidencia ${index + 1}", reference, enabled, export, openUrl)
        }
        submission.content.notes?.let { Text(it) }
        if (review != null) {
            InfoRow("Decisión", review.decision.display())
            InfoRow("Revisada", review.reviewedAt.dateLabel())
            review.observations?.let { InfoRow("Observaciones", it) }
            review.correctionDeadline?.let { InfoRow("Plazo original de corrección", it.dateLabel()) }
        }
    }
}

internal fun LazyListScope.incident(record: IncidentRecord, enabled: Boolean, model: CollaborationWorkspaceViewModel,
    export: (UUID) -> Unit, openUrl: (String) -> Unit) {
    item {
        Panel(record.category.name.display()) {
            Status(record.status.display())
            Text(record.description)
            InfoRow("Registrada", record.openedAt.dateLabel())
            record.resolvedAt?.let { InfoRow("Resuelta", it.dateLabel()) }
            record.deliverableId?.let { id ->
                Action("Ver entrega asociada", { model.open(Destination(DELIVERY, record.collaborationId, id)) }, secondary = true, enabled = enabled)
            }
            record.evidence.forEachIndexed { index, reference ->
                ReferenceAction("Evidencia ${index + 1}", reference, enabled, export, openUrl)
            }
        }
    }
    record.currentResolution?.let { proposal ->
        item { ResolutionPanel(proposal, enabled, export, openUrl) }
        item { key(record.id, record.version, proposal.id) { ResolutionControls(record, proposal, enabled, model) } }
    }
    item {
        if ("PROPOSE_RESOLUTION" in record.availableActions) Action("Proponer resolución",
            { model.open(Destination(PROPOSE_RESOLUTION, record.collaborationId, record.id)) }, enabled = enabled)
        Action("Historial de resoluciones", { model.open(Destination(RESOLUTIONS, record.collaborationId, record.id)) },
            secondary = true, enabled = enabled)
    }
}

@Composable
internal fun ResolutionPanel(proposal: ResolutionRecord, enabled: Boolean, export: (UUID) -> Unit, openUrl: (String) -> Unit) {
    Panel(proposal.terms.outcome.name.display()) {
        Status(proposal.status.display())
        Text(proposal.terms.description)
        InfoRow("Propuesta", proposal.proposedAt.dateLabel())
        proposal.terms.correctionDeadline?.let { InfoRow("Nuevo plazo", it.dateLabel()) }
        proposal.decidedAt?.let { InfoRow("Decisión", it.dateLabel()) }
        proposal.decisionReason?.let { InfoRow("Motivo", it) }
        proposal.terms.evidence.forEachIndexed { index, reference ->
            ReferenceAction("Evidencia ${index + 1}", reference, enabled, export, openUrl)
        }
    }
}

@Composable
private fun ResolutionControls(record: IncidentRecord, proposal: ResolutionRecord, enabled: Boolean, model: CollaborationWorkspaceViewModel) {
    var choice by remember { mutableStateOf<ResolutionDecision?>(null) }
    var reason by remember { mutableStateOf("") }
    choice?.let { action ->
        val label = when (action) {
            ResolutionDecision.ACCEPT -> "Aceptar resolución"
            ResolutionDecision.REJECT -> "Rechazar resolución"
            ResolutionDecision.WITHDRAW -> "Retirar propuesta"
        }
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text(label) },
            text = {
                if (action == ResolutionDecision.REJECT)
                    Field("Motivo del rechazo", reason, enabled) { reason = it }
                else Text(proposal.terms.outcome.name.display())
            },
            confirmButton = { TextButton({
                choice = null
                model.decideResolution(record, proposal, action, if (action == ResolutionDecision.REJECT) reason.trim() else null)
            }, enabled = enabled && (action != ResolutionDecision.REJECT || reason.trim().length in 1..2000)) { Text("Confirmar") } },
            dismissButton = { TextButton({ choice = null }) { Text("Volver") } }
        )
    }
    if ("ACCEPT_RESOLUTION" in record.availableActions)
        Action("Aceptar resolución", { choice = ResolutionDecision.ACCEPT }, enabled = enabled)
    if ("REJECT_RESOLUTION" in record.availableActions)
        Action("Rechazar resolución", { choice = ResolutionDecision.REJECT }, secondary = true, enabled = enabled)
    if ("WITHDRAW_RESOLUTION" in record.availableActions)
        Action("Retirar propuesta", { choice = ResolutionDecision.WITHDRAW }, secondary = true, enabled = enabled)
}

@Composable
private fun ReferenceAction(label: String, reference: ContentReference, enabled: Boolean,
    export: (UUID) -> Unit, openUrl: (String) -> Unit) {
    reference.assetId?.let { id -> Action("Guardar $label", { export(id) }, secondary = true, enabled = enabled) }
    reference.url?.let { url ->
        Text(url, style = MaterialTheme.typography.bodySmall)
        Action("Abrir $label", { openUrl(url) }, secondary = true, enabled = enabled && CollaborationInputs.externalUrl(url) != null)
    }
}

@Composable
internal fun Field(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, enabled = enabled)
}

@Composable
internal fun DeadlineField(text: String, enabled: Boolean, onChange: (String) -> Unit) {
    Field("Fecha y hora · aaaa-mm-dd hh:mm", text, enabled, onChange)
    Text("Zona horaria: ${ZoneId.systemDefault().id}", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ConfirmAction(title: String, description: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(onConfirm) { Text("Confirmar") } },
        dismissButton = { TextButton(onDismiss) { Text("Volver") } })
}
