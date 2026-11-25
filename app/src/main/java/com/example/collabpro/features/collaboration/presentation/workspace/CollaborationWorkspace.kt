package com.example.collabpro.features.collaboration.presentation.workspace

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.core.domain.Page as ResultPage
import com.example.collabpro.features.collaboration.application.CollaborationInputs
import com.example.collabpro.features.collaboration.domain.model.*
import com.example.collabpro.features.collaboration.presentation.workspace.CollaborationPage.*
import com.example.collabpro.features.identity.application.auth.SessionState
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CollaborationWorkspace(session: SessionState.Authenticated, model: CollaborationWorkspaceViewModel, onBack: () -> Unit) {
    val observed by model.ui.collectAsStateWithLifecycle()
    val state = observed.takeIf { it.owner == session.account.accountId && it.expiry == session.expiresAt } ?: CollaborationUiState(busy = true)
    val target = state.destination
    val content = state.content
    val enabled = !state.busy && !state.uncertain && !state.stale
    val back = { if (!model.back()) onBack() }
    val context = LocalContext.current
    val scopes = rememberCoroutineScope()
    var selectedExport by remember { mutableStateOf<AssetRecord?>(null) }
    var uploadDestination by remember { mutableStateOf<Destination?>(null) }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val started = uploadDestination
        uploadDestination = null
        if (uri != null && started != null) scopes.launch {
            try {
                val document = withContext(Dispatchers.IO) { readDocument(context, uri) }
                val current = model.ui.value
                if (current.owner == session.account.accountId && current.expiry == session.expiresAt && started == current.destination) {
                    val collaborationId = started.collaborationId ?: return@launch
                    model.upload(collaborationId, document, started.page in listOf(OPEN_INCIDENT, PROPOSE_RESOLUTION))
                }
            } catch (error: IOException) {
                model.reportLocalError(error.message ?: "No se pudo leer el archivo.")
            } catch (error: SecurityException) {
                model.reportLocalError("No tienes acceso al archivo elegido.")
            }
        }
    }
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val asset = selectedExport
        selectedExport = null
        if (uri != null && asset != null) model.export(asset) { bytes ->
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IOException("No se pudo abrir el destino.")
            }
        }
    }
    LaunchedEffect(state.export) {
        state.export?.let { asset ->
            selectedExport = asset
            model.exportHandled()
            try {
                saveDocument.launch(asset.filename)
            } catch (_: ActivityNotFoundException) {
                selectedExport = null
                model.reportLocalError("No hay un selector de destino disponible.")
            }
        }
    }
    val pickDocument: () -> Unit = {
        uploadDestination = target
        try { documentPicker.launch(allowedTypes.toTypedArray()) }
        catch (_: ActivityNotFoundException) {
            uploadDestination = null
            model.reportLocalError("No hay un selector de archivos disponible.")
        }
    }
    val export: (UUID) -> Unit = { id -> target.collaborationId?.let { model.prepareExport(id, it) } }
    val openUrl: (String) -> Unit = { url ->
        CollaborationInputs.externalUrl(url)?.let {
            try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(it))) }
            catch (_: ActivityNotFoundException) { model.reportLocalError("No hay un navegador disponible.") }
        }
    }
    LaunchedEffect(session.account.accountId, session.expiresAt) { model.enter() }
    BackHandler(enabled = state.countBack > 0 || !enabled, onBack = back)

    Page(target.page.title(), onBack = back) {
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.failure?.let { item { Notice(it.message) } }
        state.notice?.let { item { Notice(it) } }
        if (state.stale) item { Notice("Actualiza este recurso y revisa el estado antes de confirmar de nuevo.") }
        if (state.uncertain) item {
            Notice("El servidor no confirmó el resultado. Reintenta la misma operación para recuperarlo.")
            Action("Recuperar operación", model::retry, enabled = !state.busy)
            if (target.page !in listOf(PROPOSAL)) Action("Consultar estado", model::refresh, secondary = true, enabled = !state.busy)
        }
        when (content) {
            is CollaborationContent.Collaborations -> {
                if (target.page == HOME) item {
                    Action("Mis acuerdos", { model.open(Destination(AGREEMENTS)) }, enabled = enabled)
                    Action("Historial de colaboraciones", { model.open(Destination(HISTORY)) }, secondary = true, enabled = enabled)
                }
                filterList(target, CollaborationStatus.entries.map { it.name }, enabled, model)
                if (content.page.items.isEmpty()) item {
                    Notice(if (target.page == HISTORY && target.filter == null) "Aún no tienes colaboraciones en el historial." else "No hay colaboraciones para esta consulta.")
                }
                items(content.page.items, key = { it.id }) { row ->
                    val destination = if (target.page == HISTORY) HISTORY_DETAIL else DETAIL
                    LinkCard(row.snapshot.title, "${row.snapshot.brandName} · ${row.snapshot.creatorName}",
                        { model.open(Destination(destination, row.id)) }, row.status.name.display())
                }
                pagination(content.page, enabled, model)
            }
            is CollaborationContent.Agreements -> {
                filterList(target, listOf("PENDING", "ACCEPTED", "REJECTED", "EXPIRED"), enabled, model)
                if (content.page.items.isEmpty()) item { Notice("No hay acuerdos para esta consulta.") }
                items(content.page.items, key = { it.id }) { row ->
                    LinkCard(row.snapshot.title, "${row.snapshot.brandName} · ${row.snapshot.creatorName}",
                        { model.open(Destination(AGREEMENT, resourceId = row.id)) }, row.status.display())
                }
                pagination(content.page, enabled, model)
            }
            is CollaborationContent.Agreement -> agreement(content.record, enabled, model)
            is CollaborationContent.Detail -> when (target.page) {
                SUBMIT -> {
                    val record = content.record
                    val obligation = record.deliverables.find { it.deliverableId == target.resourceId }
                    val canSubmit = obligation != null && "SUBMIT_DELIVERABLE" in record.availableActions &&
                        obligation.submittedQuantity < obligation.requiredQuantity
                    item { DeliveryEditor(state.uploads, enabled && canSubmit, pickDocument, model::removeUpload,
                        onSubmit = { body -> if (obligation != null) model.submit(record.id, obligation.deliverableId, body) }) }
                }
                OPEN_INCIDENT -> item {
                    IncidentEditor(state.uploads, enabled && content.record.canOpenIncident, pickDocument, model::removeUpload) { category, description, evidence ->
                        model.report(content.record.id, category, description, target.resourceId, evidence)
                    }
                }
                else -> detail(content.record, enabled, model)
            }
            is CollaborationContent.Deliveries -> {
                filterList(target, listOf("PENDING_VALIDATION", "APPROVED", "REJECTED"), enabled, model)
                if (content.page.items.isEmpty()) item { Notice("No hay entregas para esta consulta.") }
                items(content.page.items, key = { it.id }) { row ->
                    LinkCard("Pieza ${row.pieceNumber}", "Versión ${row.currentSubmission.revision} · ${row.currentSubmission.submittedAt.dateLabel()}",
                        { model.open(Destination(DELIVERY, row.collaborationId, row.id)) }, row.status.display())
                }
                pagination(content.page, enabled, model)
            }
            is CollaborationContent.Delivery -> if (target.page == CORRECT) item {
                val piece = content.record
                key(piece.id, piece.version) {
                    DeliveryEditor(state.uploads, enabled && "SUBMIT_REVISION" in piece.availableActions,
                        pickDocument, model::removeUpload, piece.currentSubmission.content) { body -> model.correct(piece, body) }
                }
            } else delivery(content.record, enabled, model, export, openUrl)
            is CollaborationContent.Revisions -> {
                items(content.page.items, key = { it.submission.id }) { revision ->
                    SubmissionPanel(revision.submission, revision.review, enabled, export, openUrl)
                    revision.correctionExtensions.forEach {
                        Notice("Ampliación: ${it.previousDeadline.dateLabel()} → ${it.deadline.dateLabel()}")
                    }
                }
                pagination(content.page, enabled, model)
            }
            is CollaborationContent.Incidents -> {
                filterList(target, listOf("OPEN", "RESOLUTION_PROPOSED", "RESOLVED"), enabled, model)
                if (content.page.items.isEmpty()) item { Notice("No hay incidencias para esta consulta.") }
                items(content.page.items, key = { it.id }) { row ->
                    LinkCard(row.category.name.display(), row.description,
                        { model.open(Destination(INCIDENT, row.collaborationId, row.id)) }, row.status.display())
                }
                pagination(content.page, enabled, model)
            }
            is CollaborationContent.Incident -> {
                val record = content.record
                if (target.page == PROPOSE_RESOLUTION) item {
                    key(record.id, record.version) {
                        ResolutionEditor(state.uploads, state.extensionPieces, enabled && "PROPOSE_RESOLUTION" in record.availableActions,
                            pickDocument, model::removeUpload, { model.loadExtensionPieces(record.collaborationId) }) {
                            model.proposeResolution(record, it)
                        }
                    }
                } else incident(record, enabled, model, export, openUrl)
            }
            is CollaborationContent.Resolutions -> {
                if (content.page.items.isEmpty()) item { Notice("Aún no hay propuestas de resolución.") }
                items(content.page.items, key = { it.id }) { ResolutionPanel(it, enabled, export, openUrl) }
                pagination(content.page, enabled, model)
            }
            is CollaborationContent.History -> {
                detail(content.record.collaboration, enabled, model, historical = true)
                item { TermsPanel(content.record.agreement.snapshot) }
                item { SignaturesPanel(content.record.agreement) }
                item {
                    Panel("Recursos vinculados") {
                        InfoRow("Compensación", content.record.references.compensation.availability.resourceLabel())
                        InfoRow("Resultados", content.record.references.performance.availability.resourceLabel())
                    }
                }
            }
            is CollaborationContent.Timeline -> {
                if (content.page.items.isEmpty()) item { Notice("No hay hechos registrados.") }
                items(content.page.items, key = { it.id }) { event ->
                    Panel(event.type.eventLabel()) {
                        InfoRow("Fecha", event.occurredAt.dateLabel())
                        event.actorAccountId?.let { InfoRow("Participante", if (it == session.account.accountId) "Tu cuenta" else "Contraparte") }
                        if (event.reconstructed) Text("Recuperado del historial.")
                        event.details.filterKeys { it in setOf("reason", "status", "outcome", "decision", "late", "pieceNumber", "revision") }
                            .forEach { (name, value) -> InfoRow(name.eventFieldLabel(), value?.toString()?.display().orEmpty()) }
                    }
                }
                pagination(content.page, enabled, model)
            }
            null -> if (!state.busy && state.failure == null) item { Text("Cargando…") }
        }
        if (!state.busy && !state.uncertain) item {
            Action("Actualizar", model::refresh, secondary = true)
        }
    }
}

private val allowedTypes = listOf("application/pdf", "image/png", "image/jpeg", "image/webp", "video/mp4")

private fun readDocument(context: Context, uri: Uri): UploadDocument {
    if (uri.scheme != "content") throw IOException("Selecciona un documento con el selector de Android.")
    val resolver = context.contentResolver
    val mediaType = resolver.getType(uri)?.takeIf { it in allowedTypes } ?: throw IOException("Formato de archivo no admitido.")
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: throw IOException("No se pudo leer el nombre del archivo.")
    if (name.isBlank() || name.any { it.isISOControl() || it == '/' || it == '\\' })
        throw IOException("El nombre del archivo no es válido.")
    val bytes = resolver.openInputStream(uri)?.use {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val limit = 50 * 1024 * 1024
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            if (result.size() + read > limit) throw IOException("El archivo excede el límite de este servicio.")
            result.write(buffer, 0, read)
        }
        result.toByteArray()
    } ?: throw IOException("No se pudo abrir el archivo.")
    if (bytes.isEmpty()) throw IOException("El archivo está vacío.")
    return UploadDocument(name, mediaType, bytes)
}

private fun LazyListScope.pagination(page: ResultPage<*>, enabled: Boolean, model: CollaborationWorkspaceViewModel) {
    item {
        Text("Página ${page.page + 1} · ${page.total} registros")
        if (page.page > 0) TextButton({ model.page(page.page - 1) }, enabled = enabled) { Text("Anterior") }
        if ((page.page.toLong() + 1) * page.size < page.total)
            TextButton({ model.page(page.page + 1) }, enabled = enabled) { Text("Siguiente") }
    }
}

private fun LazyListScope.filterList(target: Destination, values: List<String>, enabled: Boolean, model: CollaborationWorkspaceViewModel) {
    item {
        Selection("Estado", target.filter, listOf(null) + values, { it?.display() ?: "Todos" }, enabled) {
            model.page(0, it)
        }
    }
}

private fun CollaborationPage.title(): String = when (this) {
    HOME -> "Mis colaboraciones"
    AGREEMENTS -> "Mis acuerdos"
    PROPOSAL, AGREEMENT -> "Acuerdo de colaboración"
    DETAIL -> "Estado de la colaboración"
    DELIVERIES -> "Entregas"
    DELIVERY -> "Detalle de entrega"
    REVISIONS -> "Versiones de la entrega"
    SUBMIT -> "Presentar entrega"
    CORRECT -> "Presentar corrección"
    INCIDENTS -> "Incidencias"
    INCIDENT -> "Detalle de incidencia"
    OPEN_INCIDENT -> "Registrar incidencia"
    PROPOSE_RESOLUTION -> "Proponer resolución"
    RESOLUTIONS -> "Historial de resoluciones"
    HISTORY -> "Historial de colaboraciones"
    HISTORY_DETAIL -> "Detalle histórico"
    TIMELINE -> "Cronología"
}

internal fun Instant.dateLabel(): String = atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"))
private fun String.resourceLabel() = if (this == "AVAILABLE") "Recurso registrado" else "Sin recurso"
internal fun String.display(): String = when (this) {
    "PENDING" -> "Pendiente"
    "ACCEPTED" -> "Aceptado"
    "REJECTED" -> "Rechazado"
    "EXPIRED" -> "Vencido"
    "ACTIVE" -> "Activa"
    "UNDER_REVIEW" -> "En revisión"
    "COMPLETED" -> "Completada"
    "CANCELLED" -> "Cancelada"
    "DISPUTED" -> "En disputa"
    "PENDING_VALIDATION" -> "Pendiente de validación"
    "APPROVED" -> "Aprobada"
    "OPEN" -> "Abierta"
    "RESOLUTION_PROPOSED" -> "Resolución propuesta"
    "RESOLVED" -> "Resuelta"
    "WITHDRAWN" -> "Retirada"
    "CONDITIONS" -> "Condiciones"
    "DELIVERABLE" -> "Entregable"
    "COMPENSATION" -> "Compensación"
    "OTHER" -> "Otro motivo"
    "KEEP_AGREEMENT" -> "Conservar el acuerdo"
    "EXTEND_CORRECTION" -> "Ampliar plazo de corrección"
    "CANCEL_COLLABORATION" -> "Cancelar colaboración"
    "CASH" -> "Pago monetario"
    "PRODUCT" -> "Producto"
    "SERVICE" -> "Servicio"
    "CREDIT" -> "Crédito"
    "BARTER" -> "Canje"
    "SUBMIT_DELIVERABLE" -> "Presentar una entrega"
    "SUBMIT_LATE_DELIVERABLE" -> "Presentar una entrega tardía"
    "CORRECT_DELIVERABLE" -> "Corregir una entrega"
    "CORRECTION_WINDOW_EXPIRED" -> "El plazo de corrección venció"
    "WAIT_FOR_REVIEW" -> "Esperar la revisión de la empresa"
    "REVIEW_DELIVERABLE" -> "Revisar entregables recibidos"
    "WAIT_FOR_DELIVERY" -> "Esperar entregables"
    "WAIT_FOR_CORRECTION" -> "Esperar corrección"
    "RESOLVE_INCIDENTS" -> "Resolver las incidencias abiertas"
    "MANUAL_CONFIRMATION" -> "Confirmación manual"
    "NICHE_EQUALS" -> "Nicho"
    "LOCATION_EQUALS" -> "Ubicación"
    "AUTHORIZED_PLATFORM" -> "Red autorizada"
    else -> this
}

private fun String.eventLabel() = when (this) {
    "AgreementGenerated" -> "Acuerdo generado"
    "BrandTermsAccepted" -> "Empresa aceptó las condiciones"
    "CreatorTermsAccepted" -> "Creador aceptó las condiciones"
    "CollaborationCreated" -> "Colaboración iniciada"
    "CollaborationCompleted" -> "Colaboración completada"
    "CollaborationCancelled" -> "Colaboración cancelada"
    "CollaborationExpired" -> "Colaboración vencida"
    "AssetUploaded" -> "Archivo adjuntado"
    "DeliverableSubmitted" -> "Contenido presentado"
    "DeliverableApproved" -> "Entrega aprobada"
    "DeliverableRejected" -> "Entrega rechazada"
    "IncidentOpened" -> "Incidencia registrada"
    "IncidentResolved" -> "Incidencia resuelta"
    else -> replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
}

private fun String.eventFieldLabel() = when (this) {
    "reason" -> "Motivo"
    "status" -> "Estado"
    "outcome" -> "Resultado"
    "decision" -> "Decisión"
    "late" -> "Fuera de plazo"
    "pieceNumber" -> "Pieza"
    "revision" -> "Versión"
    else -> this
}
