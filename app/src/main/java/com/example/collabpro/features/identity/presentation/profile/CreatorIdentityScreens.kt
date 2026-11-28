package com.example.collabpro.features.identity.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.core.domain.ApiFailure
import com.example.collabpro.core.domain.FailureKind
import com.example.collabpro.features.identity.domain.model.*

@Composable
fun CreatorProfileScreen(
    state: CreatorProfileUiState,
    onEdit: (ProfileDraft.() -> ProfileDraft) -> Unit = {},
    onSave: () -> Unit = {}, onReload: () -> Unit = {}, onDiscard: () -> Unit = {},
    onRefreshAccount: () -> Unit = {}, onSocial: () -> Unit = {}, onBack: () -> Unit = {}
) {
    var confirmReload by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    if (confirmReload || confirmDiscard) AlertDialog(onDismissRequest = { confirmReload = false; confirmDiscard = false },
        title = { Text("¿Descartar cambios sin guardar?") }, text = { Text("El texto editado se reemplazará por el perfil guardado.") },
        confirmButton = { TextButton(onClick = {
            if (confirmReload) onReload() else onDiscard()
            confirmReload = false; confirmDiscard = false
        }) { Text("Descartar") } }, dismissButton = { TextButton(onClick = { confirmReload = false; confirmDiscard = false }) { Text("Seguir editando") } })
    val busy = state.loading || state.saving
    Page("Perfil de creador", subtitle = "Administra tu contenido, audiencia y presentación para las empresas.", onBack = onBack) {
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Cargando perfil…") }
        state.failure?.let { failure -> item { IdentityFailureNotice(failure) } }
        state.notice?.let { item { Notice(it) } }
        if (state.profile != null) {
            item { ProfileField("Nombre público", state.draft.displayName, "displayName", 150, state.failure, !busy) { value -> onEdit { copy(displayName = value) } } }
            item { ProfileField("Biografía", state.draft.biography, "biography", 2000, state.failure, !busy, multiline = true) { value -> onEdit { copy(biography = value) } } }
            item { ProfileField("Nicho", state.draft.niche, "niche", 150, state.failure, !busy) { value -> onEdit { copy(niche = value) } } }
            item { ProfileField("Audiencia principal", state.draft.audienceDescription, "audienceDescription", 2000, state.failure, !busy, multiline = true) { value -> onEdit { copy(audienceDescription = value) } } }
            item { ProfileField("Ubicación", state.draft.location, "location", 150, state.failure, !busy) { value -> onEdit { copy(location = value) } } }
            if (state.dirty) item { Text("Tienes cambios sin guardar.", style = MaterialTheme.typography.labelLarge) }
            item { Action(if (state.saving) "Guardando…" else "Guardar perfil", onSave, enabled = state.dirty && !busy) }
            if (state.dirty) item { Action("Descartar cambios", { confirmDiscard = true }, secondary = true, enabled = !busy) }
            if (state.accountRefreshFailed) item { Action("Actualizar nombre de la sesión", onRefreshAccount, secondary = true, enabled = !busy) }
            item { Action("Redes sociales", onSocial, secondary = true, enabled = !busy) }
        }
        item { Action(if (state.profile == null) "Reintentar carga" else "Actualizar perfil",
            { if (state.dirty) confirmReload = true else onReload() }, secondary = true, enabled = !busy) }
        if (state.failure?.kind in listOf(FailureKind.NETWORK, FailureKind.TIMEOUT) && state.profile != null)
            item { Notice("Actualiza el perfil para comprobar si se guardaron tus cambios.") }
    }
}

@Composable
internal fun ProfileField(label: String, value: String, field: String, max: Int, failure: ApiFailure?, enabled: Boolean,
    multiline: Boolean = false, onChange: (String) -> Unit) {
    val error = failure?.fieldErrors?.get(field)
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) },
        enabled = enabled, singleLine = !multiline, minLines = if (multiline) 3 else 1, isError = error != null,
        supportingText = { Text(error ?: "${value.length}/$max") })
}

@Composable
fun LinkedSocialAccountsScreen(
    state: SocialAccountsUiState, onAuthorize: (SocialPlatform) -> Unit = {}, onReload: () -> Unit = {},
    onCheck: () -> Unit = {}, onReopenBrowser: () -> Unit = {}, onForget: () -> Unit = {}, onBack: () -> Unit = {}
) {
    var confirmForget by remember(state.link?.authorizationId, state.link?.status) { mutableStateOf(false) }
    if (confirmForget) AlertDialog(onDismissRequest = { confirmForget = false }, title = { Text("¿Dejar de seguir este intento?") },
        text = { Text("El intento dejará de mostrarse aquí. Si ya diste permiso en el navegador, la cuenta todavía podría vincularse.") },
        confirmButton = { TextButton(onClick = { confirmForget = false; onForget() }) { Text("Dejar de seguir") } },
        dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Volver") } })
    val pending = state.link?.status == AuthorizationStatus.PENDING
    val starting = state.starting != null
    Page("Redes sociales", subtitle = "Vincula una cuenta de Instagram o TikTok.", onBack = onBack) {
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Consultando cuentas vinculadas…") }
        state.failure?.let { item { IdentityFailureNotice(it) } }
        state.link?.let { link ->
            item { Panel("Autorización${link.platform?.let { " • ${it.displayLabel()}" }.orEmpty()}") {
                Text(when (link.status) {
                    AuthorizationStatus.PENDING -> "Pendiente de verificación"
                    AuthorizationStatus.SUCCEEDED -> "Vinculación confirmada"
                    AuthorizationStatus.FAILED -> "Autorización no completada"
                    AuthorizationStatus.EXPIRED -> "Autorización vencida"
                }, style = MaterialTheme.typography.titleMedium)
                link.message?.let { Text(it) }
                link.failure?.let { IdentityFailureNotice(it) }
                if (link.checking) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Comprobando vinculación…") }
                if (pending) {
                    Action("Comprobar resultado", onCheck, enabled = !link.checking && !starting)
                    if (link.failure?.code == "BROWSER_UNAVAILABLE") Action("Abrir navegador nuevamente", onReopenBrowser, secondary = true)
                    Action("Dejar de seguir este intento", { confirmForget = true }, secondary = true, enabled = !link.checking && !starting)
                }
            } }
        }
        item { Text("Cuentas vinculadas", style = MaterialTheme.typography.titleLarge) }
        if (state.loaded && !state.loading && state.failure == null && state.accounts.isEmpty()) item { Notice("Aún no tienes cuentas sociales vinculadas.") }
        if (state.loaded && state.failure != null) item { Text("La lista conserva la última consulta correcta; podría estar desactualizada.") }
        items(state.accounts, key = { it.id.toString() }) { account ->
            Panel(account.platform.displayLabel()) {
                Text(account.username, style = MaterialTheme.typography.titleMedium)
                Text(if (account.status == SocialAccountStatus.ACTIVE) "Autorizada" else "Permiso revocado")
            }
        }
        item { Action("Actualizar cuentas", onReload, secondary = true, enabled = !state.loading) }
        SocialPlatform.entries.forEach { platform -> item {
            Action(if (state.starting == platform) "Preparando autorización…" else "Vincular ${platform.displayLabel()}",
                { onAuthorize(platform) }, enabled = !pending && !starting && state.link?.checking != true)
        } }
    }
}

private fun SocialPlatform.displayLabel() = when (this) { SocialPlatform.INSTAGRAM -> "Instagram"; SocialPlatform.TIKTOK -> "TikTok" }

@Composable
internal fun IdentityFailureNotice(failure: ApiFailure) {
    val message = when (failure.code) {
        "PROVIDER_NOT_CONFIGURED" -> "Esta red social no está disponible para vincular en este momento."
        "AUTHORIZATION_NOT_FOUND" -> "El intento no existe o pertenece a otra cuenta. Inicia sesión con la cuenta que comenzó la autorización."
        "SOCIAL_ACCOUNT_ALREADY_LINKED" -> "Esta cuenta social ya está vinculada. No se creó un duplicado."
        else -> failure.message
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Text(message, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
