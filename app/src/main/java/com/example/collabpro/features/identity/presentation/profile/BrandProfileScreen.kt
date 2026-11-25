package com.example.collabpro.features.identity.presentation.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.collabpro.core.designsystem.Action
import com.example.collabpro.core.designsystem.InfoRow
import com.example.collabpro.core.designsystem.Notice
import com.example.collabpro.core.designsystem.Page
import com.example.collabpro.core.designsystem.Panel
import com.example.collabpro.features.identity.application.auth.SessionState
import com.example.collabpro.features.identity.application.profile.BrandProfileValidation
import com.example.collabpro.features.identity.domain.model.AccountType

@Composable
internal fun BrandProfileRoute(session: SessionState.Authenticated, model: BrandProfileViewModel, onBack: () -> Unit) {
    if (session.account.accountType != AccountType.BRAND) return
    val ui by model.ui.collectAsStateWithLifecycle()
    LaunchedEffect(session.account.accountId, session.expiresAt) { model.open() }
    BrandProfileScreen(
        state = model.stateFor(session, ui), onEdit = model::edit, onSave = model::save,
        onReload = model::reload, onReview = model::review, onDiscard = model::discard,
        onRefreshAccount = model::refreshAccount, onBack = onBack
    )
}

@Composable
fun BrandProfileScreen(
    state: BrandProfileUiState,
    onEdit: (BrandProfileDraft.() -> BrandProfileDraft) -> Unit = {},
    onSave: () -> Unit = {},
    onReload: () -> Unit = {},
    onReview: (Boolean) -> Unit = {},
    onDiscard: () -> Unit = {},
    onRefreshAccount: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val editable = !state.busy && state.latestForReview == null
    Page("Perfil empresarial", onBack = onBack) {
        if (state.busy) item {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(when {
                state.saving -> "Guardando perfil…"
                state.refreshingAccount -> "Actualizando el nombre en el panel…"
                else -> "Consultando perfil…"
            })
        }
        state.failure?.let { item { IdentityFailureNotice(it) } }
        state.notice?.let { item { Notice(it) } }
        if (state.needsReload && state.latestForReview == null) item {
            Notice("Consulta la versión vigente antes de volver a guardar. Tus cambios siguen en el formulario.")
        }
        state.latestForReview?.let { latest ->
            item {
                Panel("Versión actual del servidor") {
                    InfoRow("Nombre comercial", latest.businessName)
                    InfoRow("Descripción", latest.description ?: "Sin descripción")
                    InfoRow("Categoría", latest.category ?: "Sin categoría")
                    InfoRow("Ubicación", latest.location ?: "Sin ubicación")
                }
            }
            item { Notice("Puedes aplicar tus ediciones a esta versión o usar el perfil del servidor.") }
            item { Action("Conservar mis ediciones", { onReview(true) }, enabled = !state.busy) }
            item { Action("Usar perfil del servidor", { onReview(false) }, secondary = true, enabled = !state.busy) }
        }
        state.draft?.let { draft ->
            item {
                ProfileField("Nombre comercial", draft.businessName, "businessName", BrandProfileValidation.NAME_LIMIT, state.failure, editable) {
                    onEdit { copy(businessName = it) }
                }
            }
            item {
                ProfileField("Descripción", draft.description, "description", BrandProfileValidation.DESCRIPTION_LIMIT, state.failure, editable, multiline = true) {
                    onEdit { copy(description = it) }
                }
            }
            item {
                ProfileField("Categoría", draft.category, "category", BrandProfileValidation.CATEGORY_LIMIT, state.failure, editable) {
                    onEdit { copy(category = it) }
                }
            }
            item {
                ProfileField("Ubicación", draft.location, "location", BrandProfileValidation.LOCATION_LIMIT, state.failure, editable) {
                    onEdit { copy(location = it) }
                }
            }
            item { Action("Guardar perfil", onSave, enabled = state.canSave) }
            if (state.canSave) item { Action("Descartar cambios", onDiscard, secondary = true) }
        }
        if (!state.loading) item {
            Action(
                if (state.profile == null) "Reintentar carga" else "Consultar versión del servidor",
                onReload, secondary = true, enabled = !state.busy
            )
        }
        state.accountRefreshFailure?.let { failure ->
            item { Notice("No se pudo actualizar el nombre del panel. ${failure.message}") }
            item { Action("Actualizar nombre del panel", onRefreshAccount, secondary = true, enabled = !state.busy) }
        }
    }
}
