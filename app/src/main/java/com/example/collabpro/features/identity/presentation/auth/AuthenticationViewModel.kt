package com.example.collabpro.features.identity.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.identity.application.auth.*
import com.example.collabpro.features.identity.application.usecases.IdentityUseCases
import com.example.collabpro.features.identity.domain.model.AccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val useCases: IdentityUseCases,
    private val authentication: AuthenticationSession,
    private val clock: Clock
) : ViewModel() {
    val session = authentication.state
    private val mutableUi = MutableStateFlow(AuthenticationUiState())
    val ui = mutableUi.asStateFlow()
    private val navigation = Channel<AuthEvent>(Channel.BUFFERED)
    val events = navigation.receiveAsFlow()
    private var form = AuthForm.PUBLIC
    private var formRevision = 0L
    private var formJob: Job? = null
    private var restoreJob: Job? = null
    private var resetToken: String? = null // memory only; never SavedStateHandle, navigation or a composable argument.

    init {
        viewModelScope.launch { authentication.observeInvalidations() }
        viewModelScope.launch {
            session.collectLatest { current ->
                if (current is SessionState.Authenticated) {
                    mutableUi.update { it.copy(login = LoginUiState(), registration = RegistrationUiState()) }
                    val remaining = Duration.between(clock.instant(), current.expiresAt).toMillis().coerceAtLeast(0)
                    delay(remaining)
                    authentication.expire(current)
                }
            }
        }
        restoreSession()
    }

    fun restoreSession() {
        restoreJob?.cancel()
        restoreJob = viewModelScope.launch { authentication.restore() }
    }
    fun checkExpiry() {
        val current = session.value
        if (current is SessionState.Authenticated) viewModelScope.launch { authentication.expire(current) }
    }
    fun signOut() {
        cancelForm()
        restoreJob?.cancel()
        resetToken = null
        mutableUi.value = AuthenticationUiState()
        viewModelScope.launch { authentication.signOut("Sesión cerrada.") }
    }

    fun enterForm(destination: AuthForm) {
        if (form == destination) return
        cancelForm()
        form = destination
        mutableUi.update { it.copy(
            registration = if (destination == AuthForm.REGISTER_BRAND || destination == AuthForm.REGISTER_CREATOR)
                RegistrationUiState(type = if (destination == AuthForm.REGISTER_BRAND) AccountType.BRAND else AccountType.CREATOR)
                else it.registration.copy(password = "", submitting = false),
            login = it.login.copy(password = "", submitting = false, failure = null),
            recovery = it.recovery.copy(submitting = false),
            reset = if (destination == AuthForm.RESET) it.reset else ResetPasswordUiState()
        ) }
        if (destination != AuthForm.RESET) resetToken = null
    }

    private fun cancelForm() {
        formRevision++
        if (form == AuthForm.LOGIN && formJob?.isActive == true) authentication.cancelPendingAuthentication()
        formJob?.cancel()
        formJob = null
    }

    fun registrationName(value: String) = updateRegistration { copy(name = value, failure = null) }
    fun registrationEmail(value: String) = updateRegistration { copy(email = value, failure = null) }
    fun registrationPassword(value: String) = updateRegistration { copy(password = value, failure = null) }
    private fun updateRegistration(update: RegistrationUiState.() -> RegistrationUiState) {
        mutableUi.update { if (it.registration.submitting) it else it.copy(registration = it.registration.update()) }
    }
    fun loginEmail(value: String) = updateLogin { copy(email = value, failure = null) }
    fun loginPassword(value: String) = updateLogin { copy(password = value, failure = null) }
    private fun updateLogin(update: LoginUiState.() -> LoginUiState) {
        mutableUi.update { if (it.login.submitting) it else it.copy(login = it.login.update()) }
    }
    fun recoveryEmail(value: String) { mutableUi.update { if (it.recovery.submitting) it else it.copy(recovery = it.recovery.copy(email = value, failure = null, message = null)) } }
    fun newPassword(value: String) { mutableUi.update { if (it.reset.submitting || it.reset.completed) it else it.copy(reset = it.reset.copy(password = value, failure = null)) } }
    fun passwordConfirmation(value: String) { mutableUi.update { if (it.reset.submitting || it.reset.completed) it else it.copy(reset = it.reset.copy(confirmation = value, failure = null)) } }

    fun register() {
        if (form !in listOf(AuthForm.REGISTER_BRAND, AuthForm.REGISTER_CREATOR) || formJob?.isActive == true) return
        val input = ui.value.registration
        val validation = AuthInputValidation.registration(input.type, input.name, input.email, input.password)
        if (validation != null) { mutableUi.update { it.copy(registration = input.copy(failure = validation)) }; return }
        val revision = formRevision
        mutableUi.update { it.copy(registration = input.copy(submitting = true, failure = null)) }
        formJob = viewModelScope.launch {
            val result = if (input.type == AccountType.BRAND) useCases.registerBrand(input.name.trim(), input.email.trim(), input.password)
                else useCases.registerCreator(input.name.trim(), input.email.trim(), input.password)
            if (revision != formRevision) return@launch
            when (result) {
                is ApiResult.Success -> {
                    mutableUi.update { it.copy(registration = RegistrationUiState(type = input.type),
                        login = LoginUiState(email = input.email.trim(), notice = "Cuenta creada. Inicia sesión con tus credenciales.")) }
                    navigation.send(AuthEvent.GoToLogin)
                }
                is ApiResult.Failure -> mutableUi.update { it.copy(registration = input.copy(submitting = false, failure = result.error)) }
            }
        }
    }

    fun signIn() {
        if (form != AuthForm.LOGIN || formJob?.isActive == true) return
        val input = ui.value.login
        val validation = AuthInputValidation.login(input.email, input.password)
        if (validation != null) { mutableUi.update { it.copy(login = input.copy(failure = validation)) }; return }
        val revision = formRevision
        mutableUi.update { it.copy(login = input.copy(submitting = true, failure = null)) }
        formJob = viewModelScope.launch {
            val result = authentication.signIn(input.email.trim(), input.password)
            if (revision != formRevision) return@launch
            when (result) {
                is ApiResult.Success -> mutableUi.update { it.copy(login = LoginUiState()) }
                is ApiResult.Failure -> mutableUi.update { it.copy(login = input.copy(submitting = false, failure = result.error)) }
            }
        }
    }

    fun requestRecovery() {
        if (form != AuthForm.RECOVERY || formJob?.isActive == true) return
        val input = ui.value.recovery
        val validation = AuthInputValidation.recovery(input.email)
        if (validation != null) { mutableUi.update { it.copy(recovery = input.copy(failure = validation)) }; return }
        val revision = formRevision
        mutableUi.update { it.copy(recovery = input.copy(submitting = true, failure = null, message = null)) }
        formJob = viewModelScope.launch {
            val result = useCases.requestPasswordRecovery(input.email.trim())
            if (revision != formRevision) return@launch
            mutableUi.update { it.copy(recovery = when (result) {
                is ApiResult.Success -> input.copy(submitting = false, message = result.value)
                is ApiResult.Failure -> input.copy(submitting = false, failure = result.error)
            }) }
        }
    }

    fun openPasswordReset(rawLink: String) {
        cancelForm()
        form = AuthForm.RESET
        val link = PasswordResetLink.parse(rawLink)
        resetToken = (link as? PasswordResetLink.Valid)?.token
        mutableUi.update { it.copy(login = it.login.copy(password = "", submitting = false),
            registration = it.registration.copy(password = "", submitting = false),
            reset = ResetPasswordUiState(isOpen = true, linkValid = resetToken != null)) }
    }
    fun dismissPasswordReset() { enterForm(AuthForm.PUBLIC) }

    fun resetPassword() {
        if (form != AuthForm.RESET || formJob?.isActive == true || ui.value.reset.completed) return
        val token = resetToken ?: return
        val input = ui.value.reset
        val validation = AuthInputValidation.reset(input.password, input.confirmation)
        if (validation != null) { mutableUi.update { it.copy(reset = input.copy(failure = validation)) }; return }
        val revision = formRevision
        mutableUi.update { it.copy(reset = input.copy(submitting = true, failure = null)) }
        formJob = viewModelScope.launch {
            val result = useCases.resetPassword(token, input.password)
            if (revision != formRevision) return@launch
            when (result) {
                is ApiResult.Success -> {
                    // 204 confirms reset. The server revokes previous JWTs; never reuse the local session.
                    val cleared = authentication.signOut("Contraseña actualizada. Inicia sesión de nuevo.")
                    if (revision != formRevision) return@launch
                    resetToken = null
                    mutableUi.update { it.copy(reset = ResetPasswordUiState(isOpen = true, completed = true),
                        login = LoginUiState(notice = if (cleared is ApiResult.Failure) "Contraseña actualizada. ${cleared.error.message}" else "Contraseña actualizada. Inicia sesión de nuevo.")) }
                }
                is ApiResult.Failure -> mutableUi.update { it.copy(reset = input.copy(submitting = false, failure = result.error)) }
            }
        }
    }
    fun finishReset() {
        dismissPasswordReset()
        viewModelScope.launch { navigation.send(AuthEvent.GoToLogin) }
    }
}
