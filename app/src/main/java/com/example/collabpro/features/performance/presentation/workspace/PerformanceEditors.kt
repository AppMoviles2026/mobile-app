package com.example.collabpro.features.performance.presentation.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.features.performance.application.PerformanceInputs
import com.example.collabpro.features.performance.domain.model.*
import java.util.UUID

@Composable
internal fun MetricsEditor(state: PerformanceState, model: PerformanceViewModel, enabled: Boolean) {
    var socialId by rememberSaveable { mutableStateOf<String?>(null) }
    var submissionId by rememberSaveable { mutableStateOf<String?>(null) }
    val accounts = state.socials.orEmpty()
    val submissions = state.submissions.orEmpty().filter { it.postId != null }
    val social = accounts.firstOrNull { it.id.toString() == socialId } ?: accounts.singleOrNull()
    val submission = submissions.firstOrNull { it.id.toString() == submissionId } ?: submissions.singleOrNull()
    Panel("Consultar métricas de TikTok") {
        Text("Selecciona una cuenta vinculada y la versión vigente de una entrega con enlace directo al video. TikTok requiere permiso de lectura de videos.")
        Action("Consultar cuentas y entregas", { model.loadSources(automatic = true) }, secondary = true, enabled = enabled)
        if (state.socials?.isEmpty() == true) Notice("No hay una cuenta TikTok autorizada visible. Puedes consultar o aportar evidencia manual.")
        if (state.submissions != null && submissions.isEmpty()) Notice("No hay entregas vigentes con un enlace directo a un video de TikTok.")
        if (accounts.isNotEmpty()) Picker("Cuenta TikTok", accounts, social, enabled, { "@${it.username}" }) { socialId = it.id.toString() }
        if (submissions.isNotEmpty()) Picker("Entrega", submissions, submission, enabled, SubmissionOption::label) { submissionId = it.id.toString() }
        Action("Consultar métricas", {
            if (submission != null && social != null) model.refreshMetrics(social, submission)
        }, enabled = enabled && submission != null && social != null)
    }
}

@Composable
internal fun EvidenceEditor(state: PerformanceState, model: PerformanceViewModel, enabled: Boolean, pickFile: () -> Unit) {
    var opened by rememberSaveable { mutableStateOf(false) }
    var description by rememberSaveable { mutableStateOf("") }
    var links by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var observed by rememberSaveable { mutableStateOf("") }
    var submissionId by rememberSaveable { mutableStateOf<String?>(null) }
    var metricInput by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Panel("Registrar evidencia manual") {
        if (!opened) {
            Action("Añadir evidencia", { opened = true }, enabled = enabled)
            return@Panel
        }
        Input("Descripción", description, { description = it.take(4000) }, enabled, multiline = true)
        Input("Enlaces HTTPS · uno por línea", links, { links = it.take(20_480) }, enabled, multiline = true)
        state.uploads.forEach { asset ->
            InfoRow("Archivo", asset.filename)
            Action("Quitar archivo", { model.removeUpload(asset) }, secondary = true, enabled = enabled)
        }
        Action("Adjuntar archivo", pickFile, secondary = true, enabled = enabled && state.uploads.size < 10)
        Text("PDF, PNG, JPEG, WebP o MP4. Hasta 10 MiB por archivo.")
        Action("Consultar entregas para asociar", { model.loadSources(automatic = false) }, secondary = true, enabled = enabled)
        state.submissions?.takeIf { it.isNotEmpty() }?.let { entries ->
            val choice = entries.firstOrNull { it.id.toString() == submissionId }
            Picker("Versión de entrega · opcional", entries, choice, enabled, SubmissionOption::label) { submissionId = it.id.toString() }
            if (choice != null) Action("Quitar asociación", { submissionId = null }, secondary = true, enabled = enabled)
        }
        TimestampHint()
        Input("Inicio del periodo", start, { start = it }, enabled)
        Input("Fin del periodo", end, { end = it }, enabled)
        Input("Fecha de observación", observed, { observed = it }, enabled)
        Action("Usar el instante actual", {
            val now = model.now().toString()
            if (start.isBlank()) start = now
            end = now
            observed = now
        }, secondary = true, enabled = enabled)
        Input("Contadores opcionales · VIEWS=125, LIKES=10", metricInput, { metricInput = it.take(300) }, enabled)
        error?.let { Notice(it) }
        Action("Guardar evidencia", {
            val urls = links.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
            val date = PerformanceInputs.instant(observed)
            val period = date?.let { PerformanceInputs.period(start, end, it) }
            val counts = declaredCounts(metricInput)
            error = when {
                description.isBlank() -> "Escribe una descripción."
                urls.any { PerformanceInputs.https(it) == null } || urls.size != urls.distinct().size -> "Ingresa enlaces HTTPS distintos y sin credenciales."
                urls.size + state.uploads.size !in 1..10 -> "Incluye entre una y diez referencias."
                date == null || date > model.now() || period == null -> "Usa fechas válidas con inicio ≤ fin ≤ observación ≤ ahora."
                counts == null -> "Usa contadores enteros no negativos y nombres de métricas válidos."
                else -> null
            }
            if (error == null && date != null && period != null && counts != null) {
                val references = urls.map { Reference(url = it) } + state.uploads.map { Reference(assetId = it.id) }
                val submission = state.submissions?.firstOrNull { it.id.toString() == submissionId }?.id
                model.register(RegisterEvidence(UUID.randomUUID(), submission, description.trim(), references, counts, date, period))
            }
        }, enabled = enabled)
    }
}

private fun declaredCounts(text: String): Map<String, Long>? {
    if (text.isBlank()) return emptyMap()
    val metrics = linkedMapOf<String, Long>()
    for (entry in text.split(',')) {
        val fields = entry.split('=', limit = 2)
        val name = fields.first().trim().uppercase()
        val value = fields.getOrNull(1)?.trim()?.toLongOrNull() ?: return null
        if (name !in PerformanceInputs.metrics || value < 0 || metrics.put(name, value) != null) return null
    }
    return metrics
}

@Composable
internal fun ReviewEditor(entry: Evidence, model: PerformanceViewModel, enabled: Boolean) {
    var notes by rememberSaveable { mutableStateOf("") }
    var decision by rememberSaveable { mutableStateOf(EvidenceDecision.VERIFIED.name) }
    Panel("Revisión empresarial") {
        Text("La revisión conserva el origen manual de la evidencia.")
        Picker("Decisión", EvidenceDecision.entries, EvidenceDecision.valueOf(decision), enabled,
            { if (it == EvidenceDecision.VERIFIED) "Verificar evidencia" else "Rechazar evidencia" }) { decision = it.name }
        Input("Observaciones", notes, { notes = it.take(2000) }, enabled, multiline = true)
        Action("Registrar revisión", {
            model.review(entry, EvidenceDecision.valueOf(decision), notes.trim().ifBlank { null })
        }, enabled = enabled && (decision != EvidenceDecision.REJECTED.name || notes.isNotBlank()))
    }
}

@Composable
internal fun PeriodEditor(period: Period?, model: PerformanceViewModel, enabled: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf(period?.start?.toString().orEmpty()) }
    var end by rememberSaveable { mutableStateOf(period?.end?.toString().orEmpty()) }
    var error by rememberSaveable { mutableStateOf(false) }
    Panel("Periodo de atribución") {
        if (!expanded) {
            Action("Filtrar por periodo", { expanded = true }, secondary = true, enabled = enabled)
            return@Panel
        }
        TimestampHint()
        Input("Desde · inclusive", start, { start = it }, enabled)
        Input("Hasta · exclusivo", end, { end = it }, enabled)
        Action("Usar ahora como fin", { end = model.now().toString() }, secondary = true, enabled = enabled)
        if (error) Notice("Completa ambas fechas. El fin debe ser igual o posterior al inicio y hasta el instante actual.")
        Action("Aplicar periodo", {
            val next = PerformanceInputs.period(start, end, model.now())
            error = next == null
            if (next != null) model.filterAttribution(next)
        }, enabled = enabled)
        if (period != null) Action("Usar periodo completo", {
            start = ""
            end = ""
            error = false
            model.filterAttribution(null)
        }, secondary = true, enabled = enabled)
    }
}

@Composable
internal fun MechanismEditor(destinations: List<Destination>, model: PerformanceViewModel, enabled: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(MechanismKind.CODE.name) }
    var label by rememberSaveable { mutableStateOf("") }
    var destinationId by rememberSaveable { mutableStateOf<String?>(null) }
    val type = MechanismKind.valueOf(kind)
    val destination = destinations.firstOrNull { it.id == destinationId }
    Panel("Crear mecanismo") {
        if (!expanded) {
            Action("Añadir enlace o código", { expanded = true }, enabled = enabled)
            return@Panel
        }
        Picker("Tipo", MechanismKind.entries, type, enabled, { it.name.performanceLabel() }) { kind = it.name }
        Input("Nombre", label, { label = it.take(120) }, enabled)
        if (type == MechanismKind.LINK) {
            if (destinations.isEmpty()) Notice("El servidor no ofrece destinos aprobados. Puedes generar un código.")
            else Picker("Destino aprobado", destinations, destination, enabled, { "${it.id} · ${it.url}" }) { destinationId = it.id }
        }
        Action("Generar ${type.name.performanceLabel().lowercase()}", {
            model.create(type, label.trim(), if (type == MechanismKind.LINK) destination else null)
        }, enabled = enabled && label.isNotBlank() && (type == MechanismKind.CODE || destination != null))
    }
}

@Composable
internal fun InteractionEditor(mechanism: Mechanism, evidence: List<Evidence>?, lastInteraction: UUID?, model: PerformanceViewModel, enabled: Boolean) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf(InteractionType.INQUIRY.name) }
    var date by rememberSaveable { mutableStateOf("") }
    var dateError by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(lastInteraction) {
        if (lastInteraction != null) { selectedId = null; date = ""; dateError = false }
    }
    val choice = evidence?.firstOrNull { it.id.toString() == selectedId }
    val types = if (mechanism.kind == MechanismKind.CODE) InteractionType.entries else listOf(InteractionType.INQUIRY)
    Panel("Declarar interacción con evidencia") {
        Action("Consultar evidencia elegible", model::loadSupportingEvidence, secondary = true, enabled = enabled)
        if (evidence?.isEmpty() == true) Notice("No hay evidencia del mismo tipo de demostración y sin rechazo para esta colaboración.")
        if (!evidence.isNullOrEmpty()) {
            Picker("Tipo", types, InteractionType.valueOf(kind), enabled, { it.name.performanceLabel() }) { kind = it.name }
            Picker("Evidencia", evidence, choice, enabled, { it.description.take(110) }) { selectedId = it.id.toString() }
            TimestampHint()
            Input("Instante del hecho", date, { date = it }, enabled)
            Action("Usar ahora", { date = model.now().toString() }, secondary = true, enabled = enabled)
            if (dateError) Notice("La fecha debe estar entre la creación del mecanismo y el instante actual.")
            Action("Registrar declaración", {
                val at = PerformanceInputs.instant(date)
                dateError = at == null || at < mechanism.createdAt || at > model.now()
                if (!dateError && at != null && choice != null) model.record(mechanism, InteractionType.valueOf(kind), at, choice)
            }, enabled = enabled && choice != null)
        }
    }
}

@Composable
private fun TimestampHint() { Text("Usa fecha y hora ISO 8601 UTC, por ejemplo 2026-11-27T16:00:00Z.") }

@Composable
private fun Input(label: String, value: String, change: (String) -> Unit, enabled: Boolean, multiline: Boolean = false) {
    OutlinedTextField(value, change, label = { Text(label) }, enabled = enabled, singleLine = !multiline,
        maxLines = if (multiline) 4 else 1, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun <T> Picker(label: String, options: List<T>, selected: T?, enabled: Boolean, text: (T) -> String, select: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Action(selected?.let { "$label · ${text(it)}" } ?: label, { expanded = true }, secondary = true, enabled = enabled)
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { value ->
                DropdownMenuItem(text = { Text(text(value)) }, enabled = enabled, onClick = { expanded = false; select(value) })
            }
        }
    }
}
