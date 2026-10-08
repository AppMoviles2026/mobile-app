package com.example.collabpro.features.identity.presentation.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.application.auth.SessionState
import com.example.collabpro.features.identity.application.social.SocialAuthorizationReturn
import com.example.collabpro.features.identity.application.usecases.IdentityUseCases
import com.example.collabpro.features.identity.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CreatorIdentityViewModel @Inject constructor(
    private val useCases: IdentityUseCases,
    private val authentication: AuthenticationSession,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val mutableUi = MutableStateFlow(CreatorIdentityUiState())
    val ui = mutableUi.asStateFlow()
    private val browser = Channel<OpenSocialBrowser>(Channel.BUFFERED)
    val browserEvents = browser.receiveAsFlow()
    private var owner: Account? = null
    private var sessionExpiry: java.time.Instant? = null
    private var generation = 0L
    private var socialRevision = 0L
    private var checkRevision = 0L
    private var profileJob: Job? = null
    private var socialJob: Job? = null
    private var startJob: Job? = null
    private var checkJob: Job? = null
    private var queuedReturn: UUID? = savedState.get<String>(RETURN_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private var invalidReturn = false
    private var browserRequest: OpenSocialBrowser? = null

    init {
        if (queuedReturn != null) mutableUi.value = CreatorIdentityUiState(awaitingLogin = true,
            externalNotice = "Inicia sesión con la cuenta de creador que inició la autorización para verificar el resultado.")
        viewModelScope.launch { authentication.state.collect(::sessionChanged) }
    }

    private fun sessionChanged(state: SessionState) {
        val current = state as? SessionState.Authenticated
        if (current == null) {
            if (owner != null) {
                cancelRequests(); owner = null; sessionExpiry = null
                queuedReturn = null; invalidReturn = false; clearPending()
                mutableUi.value = CreatorIdentityUiState()
            }
            return
        }
        if (owner?.accountId == current.account.accountId && sessionExpiry == current.expiresAt) {
            owner = current.account
            return
        }
        cancelRequests()
        owner = current.account
        sessionExpiry = current.expiresAt
        mutableUi.value = CreatorIdentityUiState()
        if (current.account.accountType != AccountType.CREATOR) {
            clearPending()
            if (queuedReturn != null || invalidReturn) mutableUi.update { it.copy(externalNotice = "La vinculación de redes requiere una cuenta de creador.") }
            queuedReturn = null; invalidReturn = false
            return
        }
        val pendingOwner = savedState.get<String>(PENDING_OWNER)
        val pendingId = savedState.get<String>(PENDING_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val platform = savedState.get<String>(PENDING_PLATFORM)?.let { name -> SocialPlatform.entries.find { it.name == name } }
        if (pendingOwner == current.account.accountId.toString() && pendingId != null) {
            mutableUi.update { it.copy(social = it.social.copy(link = SocialLinkUiState(pendingId, platform))) }
        } else clearPending()
        if (queuedReturn != null || invalidReturn) consumeReturn()
        else if (ui.value.social.link != null) checkAuthorization()
    }

    private fun cancelRequests() {
        generation++
        socialRevision++; checkRevision++
        profileJob?.cancel(); socialJob?.cancel(); startJob?.cancel(); checkJob?.cancel()
        profileJob = null; socialJob = null; startJob = null; checkJob = null
        browserRequest = null
    }
    private fun activeCreator(): Account? = owner?.takeIf {
        val session = authentication.state.value as? SessionState.Authenticated
        it.accountType == AccountType.CREATOR && session?.account?.accountId == it.accountId && session.expiresAt == sessionExpiry
    }
    private fun current(revision: Long) = revision == generation && activeCreator() != null

    fun loadProfile(force: Boolean = false) {
        val account = activeCreator() ?: return
        if (profileJob?.isActive == true || (!force && ui.value.profile.profile != null)) return
        val revision = generation
        mutableUi.update { it.copy(profile = it.profile.copy(loading = true, failure = null, notice = null)) }
        profileJob = viewModelScope.launch {
            val result = useCases.getCreatorProfile()
            if (!current(revision)) return@launch
            mutableUi.update { state -> state.copy(profile = when (result) {
                is ApiResult.Failure -> state.profile.copy(loading = false, failure = result.error)
                is ApiResult.Success -> if (result.value.profileId != account.profileId)
                    state.profile.copy(loading = false, failure = malformed())
                else CreatorProfileUiState(profile = result.value, draft = ProfileDraft.from(result.value))
            }) }
        }
    }

    fun editProfile(update: ProfileDraft.() -> ProfileDraft) {
        if (activeCreator() == null) return
        mutableUi.update { state ->
            if (state.profile.profile == null || state.profile.loading || state.profile.saving) state
            else state.copy(profile = state.profile.copy(draft = state.profile.draft.update(), failure = null, notice = null))
        }
    }
    fun discardChanges() {
        val profile = ui.value.profile
        if (activeCreator() == null || profile.loading || profile.saving || profile.profile == null) return
        mutableUi.update { it.copy(profile = profile.copy(draft = ProfileDraft.from(profile.profile), failure = null, notice = null)) }
    }

    fun saveProfile() {
        val account = activeCreator() ?: return
        val input = ui.value.profile
        if (profileJob?.isActive == true || input.profile == null || !input.dirty) return
        val revision = generation
        mutableUi.update { it.copy(profile = input.copy(saving = true, failure = null, notice = null)) }
        profileJob = viewModelScope.launch {
            when (val result = useCases.updateCreatorProfile(input.draft.request())) {
                is ApiResult.Failure -> if (current(revision)) mutableUi.update {
                    it.copy(profile = input.copy(saving = false, failure = result.error))
                }
                is ApiResult.Success -> {
                    if (!current(revision)) return@launch
                    if (result.value.profileId != account.profileId) {
                        mutableUi.update { it.copy(profile = input.copy(saving = false, failure = malformed())) }
                        return@launch
                    }
                    mutableUi.update { it.copy(profile = CreatorProfileUiState(profile = result.value,
                        draft = ProfileDraft.from(result.value), saving = true, notice = "Perfil guardado en el servidor.")) }
                    val refreshed = authentication.refreshAccount()
                    if (!current(revision)) return@launch
                    mutableUi.update { it.copy(profile = it.profile.copy(saving = false,
                        accountRefreshFailed = refreshed is ApiResult.Failure,
                        notice = if (refreshed is ApiResult.Failure) "Perfil guardado. No se pudo actualizar el nombre de la sesión: ${refreshed.error.message}"
                            else "Perfil guardado en el servidor.")) }
                }
            }
        }
    }

    fun refreshAccountName() {
        if (activeCreator() == null || profileJob?.isActive == true) return
        val revision = generation
        profileJob = viewModelScope.launch {
            val result = authentication.refreshAccount()
            if (!current(revision)) return@launch
            mutableUi.update { it.copy(profile = it.profile.copy(accountRefreshFailed = result is ApiResult.Failure,
                notice = if (result is ApiResult.Failure) result.error.message else "Nombre de la sesión actualizado.")) }
        }
    }

    fun loadSocialAccounts() {
        if (activeCreator() == null || socialJob?.isActive == true) return
        val revision = generation
        val request = ++socialRevision
        mutableUi.update { it.copy(social = it.social.copy(loading = true, failure = null)) }
        socialJob = viewModelScope.launch {
            val result = useCases.getSocialAccounts()
            if (!current(revision) || request != socialRevision) return@launch
            mutableUi.update { state -> state.copy(social = when (result) {
                is ApiResult.Success -> state.social.copy(accounts = result.value, loaded = true, loading = false)
                is ApiResult.Failure -> state.social.copy(loading = false, failure = result.error)
            }) }
        }
    }

    fun startAuthorization(platform: SocialPlatform) {
        val account = activeCreator() ?: return
        if (startJob?.isActive == true || checkJob?.isActive == true || ui.value.social.link?.status == AuthorizationStatus.PENDING) return
        val revision = generation
        mutableUi.update { it.copy(social = it.social.copy(starting = platform, failure = null, link = null)) }
        startJob = viewModelScope.launch {
            when (val result = useCases.startSocialAuthorization(platform)) {
                is ApiResult.Failure -> if (current(revision)) mutableUi.update {
                    it.copy(social = it.social.copy(starting = null, failure = result.error))
                }
                is ApiResult.Success -> {
                    if (!current(revision)) return@launch
                    val authorization = result.value
                    if (!SocialAuthorizationReturn.trustedBrowserUrl(authorization.authorizationUrl, platform)) {
                        mutableUi.update { it.copy(social = it.social.copy(starting = null, failure = malformed())) }
                        return@launch
                    }
                    trackPending(authorization.authorizationId, platform)
                    mutableUi.update { it.copy(social = it.social.copy(starting = null,
                        link = SocialLinkUiState(authorization.authorizationId, platform, message = "Autoriza en el navegador. Volver a la app no confirma la vinculación."))) }
                    val event = OpenSocialBrowser(account.accountId, authorization.authorizationId, platform, authorization.authorizationUrl)
                    browserRequest = event
                    browser.send(event)
                }
            }
        }
    }

    fun canOpenBrowser(event: OpenSocialBrowser): Boolean = activeCreator()?.accountId == event.owner &&
        ui.value.social.link?.let { it.authorizationId == event.authorizationId && it.status == AuthorizationStatus.PENDING } == true &&
        SocialAuthorizationReturn.trustedBrowserUrl(event.uri, event.platform)

    fun browserUnavailable(event: OpenSocialBrowser) {
        if (!canOpenBrowser(event)) return
        mutableUi.update { it.copy(social = it.social.copy(link = it.social.link?.copy(
            failure = ApiFailure(FailureKind.CONFIGURATION, "BROWSER_UNAVAILABLE", "No se pudo abrir el navegador. Instala o habilita un navegador e inténtalo de nuevo.")))) }
    }
    fun reopenBrowser() {
        val event = browserRequest ?: return
        if (canOpenBrowser(event)) viewModelScope.launch { browser.send(event) }
    }

    fun receiveAuthorizationReturn(rawLink: String) {
        queuedReturn = SocialAuthorizationReturn.parse(rawLink)
        invalidReturn = queuedReturn == null
        if (queuedReturn != null) savedState[RETURN_ID] = queuedReturn.toString() else savedState.remove<String>(RETURN_ID)
        val state = authentication.state.value
        if (state is SessionState.Authenticated) {
            // session observer may not yet have bound a cold-start account.
            sessionChanged(state)
            if (queuedReturn != null || invalidReturn) consumeReturn()
        } else mutableUi.update { it.copy(awaitingLogin = true,
            externalNotice = "Inicia sesión con la cuenta de creador que inició la autorización para verificar el resultado.") }
    }

    private fun consumeReturn() {
        if (activeCreator() == null) {
            queuedReturn = null; invalidReturn = false
            mutableUi.update { it.copy(awaitingLogin = false, externalNotice = "La vinculación de redes requiere una cuenta de creador.") }
            return
        }
        val id = queuedReturn
        val invalid = invalidReturn
        queuedReturn = null; invalidReturn = false
        savedState.remove<String>(RETURN_ID)
        mutableUi.update { it.copy(showSocial = true, awaitingLogin = false, externalNotice = null) }
        if (invalid || id == null) {
            mutableUi.update { it.copy(social = it.social.copy(failure = ApiFailure(FailureKind.VALIDATION,
                "INVALID_SOCIAL_RETURN", "El enlace de retorno de autorización no es válido. Comprueba el intento pendiente o inicia uno nuevo."))) }
            return
        }
        val previous = ui.value.social.link
        if (previous?.status == AuthorizationStatus.PENDING && previous.authorizationId != id) {
            mutableUi.update { it.copy(social = it.social.copy(failure = ApiFailure(FailureKind.VALIDATION,
                "MISMATCHED_SOCIAL_RETURN", "El enlace no corresponde a la autorización pendiente. No se cambió su estado."))) }
            return
        }
        checkRevision++; checkJob?.cancel(); checkJob = null
        val platform = previous?.takeIf { link -> link.authorizationId == id }?.platform
        trackPending(id, platform)
        mutableUi.update { it.copy(social = it.social.copy(link = SocialLinkUiState(id, platform))) }
        checkAuthorization()
    }

    fun consumeSocialNavigation() { mutableUi.update { it.copy(showSocial = false) } }
    fun onResume() {
        if (ui.value.social.link?.status == AuthorizationStatus.PENDING) checkAuthorization()
    }

    fun checkAuthorization() {
        if (activeCreator() == null || checkJob?.isActive == true) return
        val link = ui.value.social.link ?: return
        val revision = generation
        val request = ++checkRevision
        mutableUi.update { it.copy(social = it.social.copy(link = link.copy(checking = true, failure = null))) }
        checkJob = viewModelScope.launch {
            // Bounded retries cover a return while the backend is finishing; no endless polling.
            repeat(3) { index ->
                val result = useCases.getAuthorizationStatus(link.authorizationId)
                if (!current(revision) || request != checkRevision || ui.value.social.link?.authorizationId != link.authorizationId) return@launch
                when (result) {
                    is ApiResult.Failure -> {
                        mutableUi.update { it.copy(social = it.social.copy(link = it.social.link?.copy(checking = false, failure = result.error,
                            message = "No se pudo verificar la autorización. No se confirmó ninguna vinculación."))) }
                        return@launch
                    }
                    is ApiResult.Success -> {
                        val attempt = result.value
                        if (attempt.authorizationId != link.authorizationId || (link.platform != null && attempt.platform != link.platform)) {
                            mutableUi.update { it.copy(social = it.social.copy(link = it.social.link?.copy(checking = false, failure = malformed()))) }
                            return@launch
                        }
                        if (attempt.status == AuthorizationStatus.PENDING && index < 2) { delay(1000); return@repeat }
                        val message = when (attempt.status) {
                            AuthorizationStatus.PENDING -> "La autorización sigue pendiente. Si cerraste el navegador, aún no se ha confirmado ningún permiso."
                            AuthorizationStatus.SUCCEEDED -> "El servidor confirmó la vinculación. Revisa abajo las cuentas actualizadas."
                            AuthorizationStatus.EXPIRED -> "La autorización venció. Inicia un nuevo intento."
                            AuthorizationStatus.FAILED -> when (attempt.errorCode) {
                                "AUTHORIZATION_DENIED" -> "No concediste los permisos necesarios. No se vinculó una cuenta nueva."
                                "SOCIAL_ACCOUNT_ALREADY_LINKED" -> "Esta cuenta ya está vinculada a tu perfil. No se creó un duplicado."
                                "PROVIDER_NOT_CONFIGURED" -> "El proveedor no está configurado en el servidor."
                                "CREATOR_REQUIRED", "ACCOUNT_NOT_ACTIVE" -> "La cuenta ya no permite completar esta autorización."
                                else -> "El proveedor no pudo completar la autorización. No se confirmó una vinculación nueva."
                            }
                        }
                        mutableUi.update { it.copy(social = it.social.copy(link = SocialLinkUiState(attempt.authorizationId,
                            attempt.platform, attempt.status, message = message))) }
                        if (attempt.status == AuthorizationStatus.PENDING) trackPending(attempt.authorizationId, attempt.platform)
                        if (attempt.status != AuthorizationStatus.PENDING) {
                            clearPending(); browserRequest = null
                            socialJob?.cancel(); socialJob = null
                            loadSocialAccounts()
                        }
                        return@launch
                    }
                }
            }
        }
    }

    /** Only forgets the mobile attempt. No backend cancellation/disconnection endpoint exists. */
    fun forgetAttempt() {
        if (activeCreator() == null || checkJob?.isActive == true || startJob?.isActive == true) return
        clearPending(); browserRequest = null
        mutableUi.update { it.copy(social = it.social.copy(link = null, failure = null)) }
        loadSocialAccounts()
    }
    private fun clearPending() {
        savedState.remove<String>(PENDING_OWNER); savedState.remove<String>(PENDING_ID); savedState.remove<String>(PENDING_PLATFORM)
        savedState.remove<String>(RETURN_ID)
    }
    private fun trackPending(id: UUID, platform: SocialPlatform?) {
        val account = activeCreator() ?: return
        savedState[PENDING_OWNER] = account.accountId.toString()
        savedState[PENDING_ID] = id.toString()
        if (platform == null) savedState.remove<String>(PENDING_PLATFORM) else savedState[PENDING_PLATFORM] = platform.name
    }
    private fun malformed() = ApiFailure(FailureKind.MALFORMED_RESPONSE, message = "El servidor devolvió datos que no corresponden a esta cuenta o autorización.")

    companion object {
        internal const val PENDING_OWNER = "social.pending.owner"
        internal const val PENDING_ID = "social.pending.id"
        internal const val PENDING_PLATFORM = "social.pending.platform"
        internal const val RETURN_ID = "social.return.id"
    }
}
