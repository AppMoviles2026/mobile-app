package com.example.collabpro.features.identity.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.collabpro.core.application.security.ExpectedAccount
import com.example.collabpro.core.domain.ApiFailure
import com.example.collabpro.core.domain.ApiResult
import com.example.collabpro.core.domain.FailureKind
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.application.auth.SessionState
import com.example.collabpro.features.identity.application.usecases.IdentityUseCases
import com.example.collabpro.features.identity.domain.model.AccountType
import com.example.collabpro.features.identity.domain.model.BusinessProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrandProfileViewModel @Inject constructor(
    private val useCases: IdentityUseCases,
    private val authentication: AuthenticationSession
) : ViewModel() {
    private val mutableUi = MutableStateFlow(BrandProfileUiState())
    val ui = mutableUi.asStateFlow()
    private var owner: BrandProfileSession? = null
    private var operation: Job? = null

    init {
        viewModelScope.launch {
            authentication.state.collect { synchronizeSession() }
        }
    }

    private fun currentOwner(): BrandProfileSession? {
        val session = authentication.state.value as? SessionState.Authenticated ?: return null
        val account = session.account
        return if (account.accountType == AccountType.BRAND)
            BrandProfileSession(account.accountId, account.profileId, session.expiresAt)
        else null
    }

    private fun synchronizeSession(): BrandProfileSession? {
        val current = currentOwner()
        if (owner != current) {
            operation?.cancel()
            owner = current
            mutableUi.value = BrandProfileUiState()
        }
        return current
    }

    internal fun stateFor(session: SessionState.Authenticated, state: BrandProfileUiState): BrandProfileUiState {
        val expected = BrandProfileSession(session.account.accountId, session.account.profileId, session.expiresAt)
        return if (session.account.accountType == AccountType.BRAND && owner == expected) state
        else BrandProfileUiState(loading = true)
    }

    fun open() {
        if (synchronizeSession() == null) return
        val state = ui.value
        if (!state.dirty && state.latestForReview == null) reload()
    }

    fun reload() {
        val account = synchronizeSession() ?: return
        val previous = ui.value
        if (previous.busy) return
        mutableUi.value = previous.copy(loading = true, failure = null, notice = null)
        operation = viewModelScope.launch(ExpectedAccount(account.accountId, account.expiresAt)) {
            val result = useCases.getBrandProfile()
            if (account != currentOwner()) return@launch
            when (result) {
                is ApiResult.Failure -> mutableUi.value = previous.copy(failure = result.error, notice = null)
                is ApiResult.Success -> {
                    val latest = result.value
                    if (!matches(latest, account, previous.profile)) {
                        mutableUi.value = previous.copy(failure = invalidProfile(), notice = null)
                        return@launch
                    }
                    val matchesDraft = previous.draft?.update(latest.version) ==
                        BrandProfileDraft.from(latest).update(latest.version)
                    mutableUi.value = when {
                        !previous.dirty || matchesDraft -> savedProfile(
                            latest, if (previous.needsReload && matchesDraft) "El servidor ya tiene estos cambios." else null
                        )
                        latest.version != previous.profile?.version || previous.needsReload ->
                            previous.copy(loading = false, failure = null, notice = null, latestForReview = latest)
                        else -> previous.copy(loading = false, failure = null, notice = "El perfil del servidor sigue en la misma versión.")
                    }
                    refreshNameIfNeeded(account, latest)
                }
            }
        }
    }

    fun edit(change: BrandProfileDraft.() -> BrandProfileDraft) {
        if (synchronizeSession() == null) return
        val state = ui.value
        val draft = state.draft ?: return
        if (state.busy || state.latestForReview != null) return
        mutableUi.value = state.copy(
            draft = draft.change(), notice = null,
            failure = state.failure?.takeUnless { it.kind == FailureKind.VALIDATION }
        )
    }

    fun review(keepEdits: Boolean) {
        if (synchronizeSession() == null) return
        val state = ui.value
        if (state.busy) return
        val latest = state.latestForReview ?: return
        val original = state.profile ?: return
        val draft = state.draft ?: return
        mutableUi.value = state.copy(
            profile = latest,
            draft = if (keepEdits) draft.reapply(original, latest) else BrandProfileDraft.from(latest),
            latestForReview = null, needsReload = false, failure = null,
            notice = if (keepEdits) "Revisa los campos y guarda para aplicar tus cambios." else null
        )
    }

    fun discard() {
        if (synchronizeSession() == null) return
        val state = ui.value
        val profile = state.profile ?: return
        if (state.busy || state.needsReload || state.latestForReview != null) return
        mutableUi.value = state.copy(draft = BrandProfileDraft.from(profile), failure = null, notice = null)
    }

    fun save() {
        val account = synchronizeSession() ?: return
        val state = ui.value
        if (!state.canSave) return
        val profile = state.profile ?: return
        val update = state.draft?.update(profile.version) ?: return
        mutableUi.value = state.copy(saving = true, failure = null, notice = null)
        operation = viewModelScope.launch(ExpectedAccount(account.accountId, account.expiresAt)) {
            val result = useCases.updateBrandProfile(update)
            if (account != currentOwner()) return@launch
            when (result) {
                is ApiResult.Failure -> mutableUi.value = state.copy(
                    failure = result.error,
                    notice = null,
                    needsReload = result.error.kind in setOf(
                        FailureKind.CONFLICT, FailureKind.TIMEOUT, FailureKind.NETWORK,
                        FailureKind.SERVER, FailureKind.PROVIDER_UNAVAILABLE, FailureKind.MALFORMED_RESPONSE
                    )
                )
                is ApiResult.Success -> {
                    val saved = result.value
                    if (!matches(saved, account, profile)) {
                        mutableUi.value = state.copy(failure = invalidProfile(), needsReload = true, notice = null)
                        return@launch
                    }
                    mutableUi.value = savedProfile(saved, "Perfil guardado.")
                    refreshNameIfNeeded(account, saved)
                }
            }
        }
    }

    fun refreshAccount() {
        val account = synchronizeSession() ?: return
        if (ui.value.busy) return
        operation = viewModelScope.launch(ExpectedAccount(account.accountId, account.expiresAt)) {
            refreshName(account)
        }
    }

    private suspend fun refreshNameIfNeeded(account: BrandProfileSession, profile: BusinessProfile) {
        val session = authentication.state.value as? SessionState.Authenticated
        if (session?.account?.name != profile.businessName) refreshName(account)
    }

    private suspend fun refreshName(account: BrandProfileSession) {
        if (account != currentOwner()) return
        mutableUi.value = ui.value.copy(refreshingAccount = true, accountRefreshFailure = null)
        val result = authentication.refreshAccount()
        if (account != currentOwner()) return
        mutableUi.value = ui.value.copy(
            refreshingAccount = false, accountRefreshFailure = (result as? ApiResult.Failure)?.error
        )
    }

    private fun savedProfile(profile: BusinessProfile, notice: String?) = BrandProfileUiState(
        profile = profile, draft = BrandProfileDraft.from(profile), notice = notice
    )

    private fun matches(profile: BusinessProfile, account: BrandProfileSession, previous: BusinessProfile?) =
        profile.profileId == account.profileId && (previous == null || profile.version >= previous.version)

    private fun invalidProfile() = ApiFailure(
        FailureKind.MALFORMED_RESPONSE, message = "El servidor devolvió un perfil que no coincide con la cuenta o la versión consultada."
    )
}
