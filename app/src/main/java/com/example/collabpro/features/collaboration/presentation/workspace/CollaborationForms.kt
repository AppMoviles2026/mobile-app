package com.example.collabpro.features.collaboration.presentation.workspace

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.example.collabpro.core.designsystem.Action
import com.example.collabpro.core.designsystem.InfoRow
import com.example.collabpro.core.designsystem.Notice
import com.example.collabpro.core.designsystem.Panel
import com.example.collabpro.features.collaboration.application.CollaborationInputs
import com.example.collabpro.features.collaboration.domain.model.*
import java.time.Instant
import java.util.UUID

@Composable
internal fun DeliveryEditor(uploads: List<AssetRecord>, enabled: Boolean,
    onPick: () -> Unit, onRemove: (UUID) -> Unit, previous: DeliveryBody? = null, onSubmit: (DeliveryBody) -> Unit) {
    var contentUrl by remember { mutableStateOf(previous?.content?.url.orEmpty()) }
    var contentAsset by remember { mutableStateOf(previous?.content?.assetId) }
    var evidenceLinks by remember { mutableStateOf(previous?.evidence.orEmpty().mapNotNull { it.url }.joinToString("\n")) }
    var keepPreviousEvidence by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf(previous?.notes.orEmpty()) }
    var attempted by remember { mutableStateOf(false) }
    val choices = (listOf(null, previous?.content?.assetId) + uploads.map { it.id }).distinct()
    val linkedAsset = contentAsset?.takeIf { it in choices }
    val reference = linkedAsset?.let { ContentReference(assetId = it) }
        ?: CollaborationInputs.externalUrl(contentUrl)?.let { ContentReference(url = it) }
    val existing = if (keepPreviousEvidence) previous?.evidence.orEmpty().filter { it.assetId != null } else emptyList()
    val files = existing + uploads.filterNot { it.id == linkedAsset }.map { ContentReference(assetId = it.id) }
    val evidence = CollaborationInputs.references(evidenceLinks, files)?.filterNot { it == reference }
    Panel("Contenido y evidencias") {
        if (!enabled) Text("Consulta el estado de la entrega y las acciones disponibles.")
        if (choices.size > 1) Selection("Contenido", linkedAsset, choices,
            { id -> uploads.find { it.id == id }?.filename ?: if (id == null) "Enlace HTTPS" else "Archivo de la versión anterior" },
            enabled) { contentAsset = it }
        if (linkedAsset == null) Field("Enlace HTTPS del contenido", contentUrl, enabled) { contentUrl = it }
        Field("Enlaces de evidencia · uno por línea", evidenceLinks, enabled) { evidenceLinks = it }
        if (previous?.evidence.orEmpty().any { it.assetId != null }) {
            TextButton({ keepPreviousEvidence = !keepPreviousEvidence }, enabled = enabled) {
                Text(if (keepPreviousEvidence) "Quitar archivos de la versión anterior" else "Usar archivos de la versión anterior")
            }
        }
        Uploads(uploads, enabled, onPick, onRemove)
        Field("Notas opcionales", notes, enabled) { notes = it }
        if (attempted && (reference == null || evidence.isNullOrEmpty() || notes.trim().length > 4000))
            Notice("Indica el contenido, entre 1 y 10 evidencias distintas y hasta 4000 caracteres de notas.")
        Action(if (previous == null) "Enviar entrega" else "Enviar nueva versión", {
            attempted = true
            if (reference != null && !evidence.isNullOrEmpty() && notes.trim().length <= 4000)
                onSubmit(DeliveryBody(reference, evidence, notes.trim().ifEmpty { null }))
        }, enabled = enabled)
    }
}

@Composable
internal fun IncidentEditor(uploads: List<AssetRecord>, enabled: Boolean, onPick: () -> Unit, onRemove: (UUID) -> Unit,
    onSubmit: (IncidentCategory, String, List<ContentReference>) -> Unit) {
    var category by remember { mutableStateOf(IncidentCategory.OTHER) }
    var description by remember { mutableStateOf("") }
    var links by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val evidence = CollaborationInputs.references(links, uploads.map { ContentReference(assetId = it.id) })
    Panel("Motivo de la incidencia") {
        if (!enabled) Text("Comprueba si el plazo de registro sigue disponible.")
        Selection("Categoría", category, IncidentCategory.entries, { it.name.display() }, enabled) { category = it }
        Field("Describe el problema", description, enabled) { description = it }
        Field("Evidencias HTTPS opcionales · una por línea", links, enabled) { links = it }
        Uploads(uploads, enabled, onPick, onRemove)
        if (attempted && (description.trim().length !in 1..4000 || evidence == null))
            Notice("Escribe una descripción de hasta 4000 caracteres. Puedes adjuntar hasta 10 evidencias.")
        Action("Registrar incidencia", {
            attempted = true
            if (description.trim().length in 1..4000 && evidence != null) onSubmit(category, description.trim(), evidence)
        }, enabled = enabled)
    }
}

@Composable
internal fun ResolutionEditor(uploads: List<AssetRecord>, pieces: List<DeliveryRecord>, enabled: Boolean,
    onPick: () -> Unit, onRemove: (UUID) -> Unit, onLoadPieces: () -> Unit, onSubmit: (ResolutionTerms) -> Unit) {
    var outcome by remember { mutableStateOf(ResolutionOutcome.KEEP_AGREEMENT) }
    var description by remember { mutableStateOf("") }
    var links by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<DeliveryRecord?>(null) }
    var attempted by remember { mutableStateOf(false) }
    val extension = outcome == ResolutionOutcome.EXTEND_CORRECTION
    val deadline = if (extension) CollaborationInputs.deadline(date) else null
    // Keep the exact revision the participant inspected. A refresh must not silently target another one.
    val piece = selected?.takeIf { chosen -> pieces.any { it.id == chosen.id && it.version == chosen.version } }
    val evidence = CollaborationInputs.references(links, uploads.map { ContentReference(assetId = it.id) })
    val validExtension = !extension || (piece != null && deadline?.isAfter(Instant.now()) == true &&
        (piece.effectiveCorrectionDeadline?.let { deadline.isAfter(it) } ?: true))
    Panel("Condiciones de resolución") {
        Selection("Resultado", outcome, ResolutionOutcome.entries, { it.name.display() }, enabled) {
            outcome = it
            if (it == ResolutionOutcome.EXTEND_CORRECTION) onLoadPieces()
        }
        Field("Propuesta para la contraparte", description, enabled) { description = it }
        if (extension) {
            Selection("Entrega rechazada", piece, listOf(null) + pieces, { row ->
                row?.let { "Pieza ${it.pieceNumber} · versión ${it.currentSubmission.revision} · ${it.currentSubmission.submittedAt.dateLabel()}" }
                    ?: "Seleccionar"
            }, enabled) { selected = it }
            if (pieces.isEmpty()) Text("No hay entregas rechazadas para seleccionar.")
            Action("Actualizar entregas rechazadas", onLoadPieces, secondary = true, enabled = enabled)
            piece?.effectiveCorrectionDeadline?.let { InfoRow("Plazo vigente", it.dateLabel()) }
            DeadlineField(date, enabled) { date = it }
        }
        Field("Evidencias HTTPS opcionales · una por línea", links, enabled) { links = it }
        Uploads(uploads, enabled, onPick, onRemove)
        if (attempted && (description.trim().length !in 1..4000 || evidence == null || !validExtension))
            Notice("Revisa la descripción y los adjuntos. Una ampliación requiere elegir la versión rechazada y un plazo posterior al vigente.")
        Action("Enviar propuesta", {
            attempted = true
            if (description.trim().length in 1..4000 && evidence != null && validExtension) {
                val target = piece.takeIf { extension }
                onSubmit(ResolutionTerms(outcome, description.trim(), evidence, target?.id,
                    target?.currentSubmission?.id, target?.version, deadline))
            }
        }, enabled = enabled)
    }
}

@Composable
private fun Uploads(uploads: List<AssetRecord>, enabled: Boolean, onPick: () -> Unit, onRemove: (UUID) -> Unit) {
    uploads.forEach { asset ->
        Text(asset.filename)
        TextButton({ onRemove(asset.id) }, enabled = enabled) { Text("Quitar adjunto") }
    }
    Action("Adjuntar archivo privado", onPick, enabled = enabled && uploads.size < 10, secondary = true)
}
