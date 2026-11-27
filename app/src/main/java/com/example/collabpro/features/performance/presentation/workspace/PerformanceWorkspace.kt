package com.example.collabpro.features.performance.presentation.workspace

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.core.domain.Page as ResultPage
import com.example.collabpro.features.identity.application.auth.SessionState
import com.example.collabpro.features.performance.application.PerformanceInputs
import com.example.collabpro.features.performance.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
internal fun PerformanceWorkspace(session: SessionState.Authenticated, id: UUID, model: PerformanceViewModel, onBack: () -> Unit) {
    val observed by model.ui.collectAsStateWithLifecycle()
    val owned = observed.scope?.takeIf { it.account.accountId == session.account.accountId && it.expires == session.expiresAt }
    val state = observed.takeIf { owned?.collaboration == id } ?: PerformanceState(busy = true)
    val context = LocalContext.current
    val coroutine = rememberCoroutineScope()
    var selectedExport by remember(id) { mutableStateOf<Pair<PerformanceScope, Asset>?>(null) }
    var uploadScope by remember(id) { mutableStateOf<PerformanceScope?>(null) }
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val owner = uploadScope
        uploadScope = null
        if (uri != null && owner != null) coroutine.launch {
            try {
                val document = withContext(Dispatchers.IO) { readEvidenceDocument(context, uri) }
                if (model.ui.value.scope == owner) model.upload(document, owner.collaboration)
            } catch (error: IOException) {
                if (model.ui.value.scope == owner) model.failure(error.message ?: "No se pudo leer el archivo.")
            } catch (_: SecurityException) {
                if (model.ui.value.scope == owner) model.failure("Android no permite leer este documento.")
            }
        }
    }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val selection = selectedExport
        selectedExport = null
        if (uri != null && selection != null) model.download(selection.second, selection.first) { data ->
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: throw IOException("No se pudo guardar.")
            }
        }
    }
    LaunchedEffect(state.export) {
        val asset = state.export ?: return@LaunchedEffect
        val scope = state.scope ?: return@LaunchedEffect
        selectedExport = scope to asset
        model.clearExport()
        try { savePicker.launch(asset.filename) }
        catch (_: ActivityNotFoundException) {
            selectedExport = null
            model.failure("No hay un selector de destino disponible.")
        }
    }
    LaunchedEffect(id, session.account.accountId, session.expiresAt) { model.open(id) }
    val back = { if (owned?.collaboration != id || !model.back()) onBack() }
    BackHandler(onBack = back)
    val enabled = !state.busy && !state.uncertain
    val canWrite = enabled && state.writable
    val brand = state.scope?.brand == true
    val openReference: (String) -> Unit = { raw ->
        PerformanceInputs.https(raw)?.let { launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(it.toString())), model) }
    }
    val pick: () -> Unit = {
        uploadScope = state.scope
        try { uploadPicker.launch(fileTypes) }
        catch (_: ActivityNotFoundException) {
            uploadScope = null
            model.failure("No hay un selector de documentos disponible.")
        }
    }
    Page("Resultados", subtitle = state.title.orEmpty(), onBack = back) {
        item {
            ChoiceRow(listOf("Métricas", "Evidencia", "Atribución"), when (state.tab) {
                PerformanceTab.METRICS, PerformanceTab.SNAPSHOTS -> "Métricas"
                PerformanceTab.EVIDENCE, PerformanceTab.EVIDENCE_DETAIL -> "Evidencia"
                else -> "Atribución"
            }) { tab ->
                if (enabled) model.select(when (tab) {
                    "Métricas" -> PerformanceTab.METRICS
                    "Evidencia" -> PerformanceTab.EVIDENCE
                    else -> PerformanceTab.ATTRIBUTION
                })
            }
        }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (owned != null && owned.collaboration != id && observed.uncertain) item {
            Notice("Vuelve al resultado anterior para recuperar la operación pendiente.")
        }
        state.failure?.let { item { Notice(it) } }
        state.notice?.let { item { Notice(it) } }
        if (state.uncertain) item {
            Notice("La operación pudo haberse registrado. Recupera el intento original antes de enviar otro.")
            Action("Recuperar operación", model::retry, enabled = !state.busy)
        }
        if (state.title != null && !state.writable) item { Notice("Solo consulta. Las operaciones de escritura no están disponibles.") }
        when (state.tab) {
            PerformanceTab.METRICS -> metrics(state, model, canWrite)
            PerformanceTab.SNAPSHOTS -> state.snapshots?.let { page ->
                if (page.items.isEmpty()) item { Notice("Aún no hay capturas históricas.") }
                items(page.items, key = { it.id }) { SnapshotPanel(it) }
                pagination(page, enabled, model::page)
            }
            PerformanceTab.EVIDENCE -> {
                item {
                    val options = listOf("Todas", "UNVERIFIED", "VERIFIED", "REJECTED").associateBy {
                        if (it == "Todas") it else it.performanceLabel()
                    }
                    ChoiceRow(options.keys.toList(), state.evidenceFilter?.performanceLabel() ?: "Todas") { label ->
                        if (enabled) model.filterEvidence(options[label]?.takeUnless { it == "Todas" })
                    }
                }
                if (!brand && state.writable) item {
                    EvidenceEditor(state, model, canWrite, pick)
                }
                state.evidence?.let { page ->
                    if (page.items.isEmpty()) item { Notice("No hay evidencias en esta consulta.") }
                    items(page.items, key = { it.id }) { entry ->
                        LinkCard(entry.description, "Evidencia manual · ${entry.observedAt.displayTime()}",
                            { model.inspectEvidence(entry.id) }, entry.status.performanceLabel())
                    }
                    pagination(page, enabled, model::page)
                }
            }
            PerformanceTab.EVIDENCE_DETAIL -> state.selectedEvidence?.let { entry ->
                item { EvidencePanel(entry, openReference, model::chooseAsset, !state.busy) }
                if (brand && state.writable && entry.status == "UNVERIFIED") item(key = entry.id) {
                    ReviewEditor(entry, model, canWrite)
                }
            }
            PerformanceTab.ATTRIBUTION -> {
                item { PeriodEditor(state.period, model, enabled) }
                if (brand && state.writable) item { MechanismEditor(state.destinations.orEmpty(), model, canWrite) }
                state.attribution?.let { report ->
                    item { AttributionSummary(report) }
                    items(report.mechanisms.items, key = { it.mechanism.id }) { row ->
                        MechanismPanel(row, brand && canWrite, enabled, model,
                            share = { text ->
                                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                launch(context, Intent.createChooser(intent, "Compartir mecanismo"), model)
                            },
                            browser = { url -> launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), model) })
                    }
                    pagination(report.mechanisms, enabled, model::page)
                }
            }
            PerformanceTab.INTERACTIONS -> {
                val mechanism = state.mechanism
                if (mechanism != null) {
                    item {
                        Panel(mechanism.label) {
                            InfoRow("Estado", mechanism.availability.performanceLabel())
                            InfoRow("Periodo", state.period?.displayPeriod() ?: "Periodo del reporte")
                        }
                    }
                    if (brand && state.writable && mechanism.availability == "AVAILABLE") item(key = mechanism.id) {
                        InteractionEditor(mechanism, state.supportingEvidence, state.lastInteraction, model, canWrite)
                    }
                }
                state.interactions?.let { page ->
                    if (page.items.isEmpty()) item { Notice("No hay interacciones en este periodo.") }
                    items(page.items, key = { it.id }) { row ->
                        Panel(row.type.performanceLabel()) {
                            InfoRow("Fuente", row.source.performanceLabel())
                            InfoRow("Ocurrió", row.occurredAt.displayTime())
                            InfoRow("Registrada", row.recordedAt.displayTime())
                            InfoRow("Incluida en totales", if (row.includedInTotals) "Sí" else "No")
                            if (row.demonstration) Text("Demostración")
                            row.evidenceStatus?.let { InfoRow("Evidencia", it.performanceLabel()) }
                            row.evidenceId?.let { evidence ->
                                Action("Ver evidencia", { model.inspectEvidence(evidence) }, secondary = true, enabled = enabled)
                            }
                        }
                    }
                    pagination(page, enabled, model::page)
                }
            }
        }
        item { Action("Actualizar vista", model::refresh, secondary = true, enabled = !state.busy) }
    }
}

private fun LazyListScope.metrics(state: PerformanceState, model: PerformanceViewModel, canWrite: Boolean) {
    state.metrics?.let { report ->
        item {
            Panel("Medición automática") {
                InfoRow("Disponibilidad", report.automaticAvailability.performanceLabel())
                InfoRow("Publicaciones con datos", report.availablePublications.toString())
                InfoRow("Evidencias manuales", report.manualEvidenceCount.toString())
                if (report.automaticAvailability == "NO_DATA") Notice("Aún no hay métricas automáticas disponibles. Consulta la evidencia manual.")
                Action("Historial de capturas", { model.select(PerformanceTab.SNAPSHOTS) }, secondary = true,
                    enabled = !state.busy && !state.uncertain)
            }
        }
        items(report.publications.items, key = { it.publication.id }) { row ->
            Panel("${row.publication.platform.performanceLabel()} · ${row.publication.postId}") {
                InfoRow("Disponibilidad", row.availability.performanceLabel())
                row.lastRefresh?.let { InfoRow("Última consulta", "${it.status.performanceLabel()} · ${it.checkedAt.displayTime()}") }
                row.bindings.forEach { InfoRow("Publicación vinculada", it.publicationUrl) }
                row.snapshot?.let { SnapshotFacts(it) }
            }
        }
        pagination(report.publications, !state.busy && !state.uncertain, model::page)
    }
    if (state.writable) item { MetricsEditor(state, model, canWrite) }
}

@Composable
internal fun SnapshotFacts(snapshot: Snapshot) {
    InfoRow("Fuente", "${snapshot.sourceType.performanceLabel()} · ${snapshot.provider.performanceLabel()}")
    InfoRow("Periodo", snapshot.period.displayPeriod())
    InfoRow("Ventana", snapshot.periodKind.performanceLabel())
    InfoRow("Consultada", snapshot.capturedAt.displayTime())
    snapshot.metrics.forEach { (metric, value) -> InfoRow(metric.performanceLabel(), value.toString()) }
}

@Composable
private fun SnapshotPanel(snapshot: Snapshot) = Panel("Captura de métricas") { SnapshotFacts(snapshot) }

@Composable
internal fun EvidencePanel(entry: Evidence, open: (String) -> Unit, download: (UUID) -> Unit, enabled: Boolean) {
    Panel("Evidencia manual") {
        InfoRow("Estado", entry.status.performanceLabel())
        if (entry.demonstration) Text("Demostración")
        Text(entry.description)
        InfoRow("Fuente", entry.sourceType.performanceLabel())
        InfoRow("Observada", entry.observedAt.displayTime())
        InfoRow("Periodo", entry.period.displayPeriod())
        entry.reviewedAt?.let { InfoRow("Revisada", it.displayTime()) }
        entry.observations?.let { InfoRow("Observaciones", it) }
        entry.claimedMetrics.forEach { (metric, value) ->
            InfoRow("${metric.performanceLabel()} · declaradas", value.toString())
        }
        entry.references.forEach { reference ->
            reference.url?.let { url -> Action("Abrir $url", { open(url) }, secondary = true, enabled = enabled) }
            reference.assetId?.let { id -> Action("Guardar archivo", { download(id) }, secondary = true, enabled = enabled) }
        }
    }
}

@Composable
internal fun AttributionSummary(report: AttributionReport) {
    Panel("Interacciones atribuibles") {
        InfoRow("Disponibilidad", report.availability.performanceLabel())
        InfoRow("Periodo", report.period.displayPeriod() + " · fin exclusivo")
        CountRows(report.totals)
        report.sources.forEach { (type, source) -> InfoRow(type.performanceLabel(), source.performanceLabel()) }
        Notice(report.warning)
    }
    Panel("Interacciones de demostración") { CountRows(report.demonstrationTotals) }
}

@Composable
private fun MechanismPanel(row: MechanismResults, mutable: Boolean, enabled: Boolean,
    model: PerformanceViewModel, share: (String) -> Unit, browser: (String) -> Unit) {
    val item = row.mechanism
    Panel(item.label) {
        InfoRow("Tipo", item.kind.name.performanceLabel())
        InfoRow("Disponibilidad", item.availability.performanceLabel())
        if (item.demonstration) Text("Demostración")
        item.code?.let { InfoRow("Código", it) }
        val publicUrl = PerformanceInputs.tracking(item.trackingUrl)?.toString()
        publicUrl?.let { InfoRow("Enlace público", it) }
        if (item.availability == "AVAILABLE") {
            (publicUrl ?: item.code)?.let { Action("Compartir", { share(it) }, secondary = true, enabled = enabled) }
            publicUrl?.let { Action("Abrir enlace público", { browser(it) }, secondary = true, enabled = enabled) }
        }
        CountRows(row.interactions)
        Action("Ver interacciones", { model.inspectInteractions(item) }, secondary = true, enabled = enabled)
        if (mutable && item.availability != "COLLABORATION_CANCELLED") {
            val label = if (item.status == MechanismStatus.ACTIVE) "Desactivar" else "Activar"
            Action(label, { model.changeStatus(item) }, secondary = true)
        }
    }
}

@Composable
private fun CountRows(counts: Counts) {
    InfoRow("Clics", counts.clicks.toString())
    InfoRow("Consultas", counts.inquiries.toString())
    InfoRow("Usos de código", counts.codeRedemptions.toString())
}

private fun LazyListScope.pagination(page: ResultPage<*>, enabled: Boolean, change: (Int) -> Unit) {
    if (page.total <= page.size) return
    item {
        Text("Página ${page.page + 1} · ${page.total} registros")
        if (page.page > 0) Action("Anterior", { change(page.page - 1) }, secondary = true, enabled = enabled)
        if ((page.page.toLong() + 1) * page.size < page.total) Action("Siguiente", { change(page.page + 1) }, secondary = true, enabled = enabled)
    }
}

private fun launch(context: Context, intent: Intent, model: PerformanceViewModel) {
    try { context.startActivity(intent) }
    catch (_: ActivityNotFoundException) { model.failure("No hay una aplicación disponible para esta acción.") }
    catch (_: SecurityException) { model.failure("Android no permite abrir esta referencia.") }
}

private val fileTypes = arrayOf("application/pdf", "image/png", "image/jpeg", "image/webp", "video/mp4")

private fun readEvidenceDocument(context: Context, uri: Uri): Document {
    if (uri.scheme != "content") throw IOException("Selecciona un archivo con el selector de Android.")
    val resolver = context.contentResolver
    val type = resolver.getType(uri)?.takeIf { it in fileTypes } ?: throw IOException("Formato de archivo no admitido.")
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }?.takeIf { it.isNotBlank() && it.none { char -> char.isISOControl() || char == '/' || char == '\\' } }
        ?: throw IOException("Nombre de archivo no válido.")
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            if (result.size() + size > 10 * 1024 * 1024) throw IOException("El archivo supera los 10 MiB.")
            result.write(buffer, 0, size)
        }
        result.toByteArray()
    } ?: throw IOException("No se pudo abrir el archivo.")
    if (bytes.isEmpty()) throw IOException("El archivo está vacío.")
    return Document(name, type, bytes)
}

private val dates = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
internal fun Instant.displayTime(): String = atZone(ZoneId.systemDefault()).format(dates)
internal fun Period.displayPeriod() = "${start.displayTime()} → ${end.displayTime()}"

internal fun String.performanceLabel(): String = when (this) {
    "AVAILABLE" -> "Disponible"
    "NO_DATA" -> "Sin métricas automáticas"
    "AVAILABLE_STALE" -> "Captura anterior disponible"
    "HISTORICAL_ONLY" -> "Solo historial"
    "AUTHORIZATION_REQUIRED" -> "Requiere autorizar la cuenta"
    "INSUFFICIENT_SCOPE" -> "Falta el permiso de lectura de videos"
    "PROVIDER_NOT_CONFIGURED" -> "Proveedor sin configurar"
    "UNSUPPORTED_PROVIDER" -> "Red sin métricas automáticas"
    "RATE_LIMITED" -> "Límite de consultas del proveedor"
    "PROVIDER_UNAVAILABLE" -> "Proveedor no disponible"
    "INVALID_RESPONSE" -> "Respuesta del proveedor no válida"
    "AUTOMATED" -> "Automática"
    "MANUAL" -> "Evidencia manual"
    "LIFETIME" -> "Acumulada desde la publicación"
    "UNVERIFIED" -> "Sin revisar"
    "VERIFIED" -> "Revisada por la empresa"
    "REJECTED" -> "Rechazada"
    "LINK" -> "Enlace"
    "CODE" -> "Código"
    "INACTIVE" -> "Inactivo"
    "DESTINATION_UNAVAILABLE" -> "Destino no disponible"
    "COLLABORATION_CANCELLED" -> "Colaboración cancelada"
    "NO_MECHANISM" -> "Sin mecanismos"
    "NO_INTERACTIONS" -> "Sin interacciones en el periodo"
    "CLICK" -> "Clic"
    "INQUIRY" -> "Consulta"
    "CODE_REDEMPTION" -> "Uso de código"
    "FIRST_PARTY_TRACKING" -> "Registro del enlace CollabPro"
    "BRAND_DECLARATION" -> "Declaración de empresa"
    "VIEWS" -> "Vistas"
    "LIKES" -> "Me gusta"
    "COMMENTS" -> "Comentarios"
    "SHARES" -> "Compartidos"
    "REACH" -> "Alcance"
    "IMPRESSIONS" -> "Impresiones"
    "ENGAGEMENT" -> "Interacciones"
    "CLICKS" -> "Clics"
    "CONVERSIONS" -> "Conversiones declaradas"
    "tiktok", "TIKTOK" -> "TikTok"
    else -> lowercase().replace('_', ' ').replaceFirstChar(Char::uppercaseChar)
}
