package com.example.collabpro.features.identity.presentation.auth

import com.example.collabpro.core.domain.ApiFailure
import com.example.collabpro.features.identity.domain.model.AccountType

data class RegistrationUiState(
    val type: AccountType = AccountType.BRAND, val name: String = "", val email: String = "",
    val password: String = "", val submitting: Boolean = false, val failure: ApiFailure? = null
) { override fun toString() = "RegistrationUiState(type=$type, submitting=$submitting, fields=<redacted>)" }
data class LoginUiState(
    val email: String = "", val password: String = "", val submitting: Boolean = false,
    val failure: ApiFailure? = null, val notice: String? = null
) { override fun toString() = "LoginUiState(submitting=$submitting, fields=<redacted>)" }
data class RecoveryUiState(val email: String = "", val submitting: Boolean = false, val failure: ApiFailure? = null, val message: String? = null)
data class ResetPasswordUiState(
    val isOpen: Boolean = false, val linkValid: Boolean = false, val password: String = "", val confirmation: String = "",
    val submitting: Boolean = false, val failure: ApiFailure? = null, val completed: Boolean = false
) { override fun toString() = "ResetPasswordUiState(linkValid=$linkValid, submitting=$submitting, fields=<redacted>)" }
data class AuthenticationUiState(
    val registration: RegistrationUiState = RegistrationUiState(), val login: LoginUiState = LoginUiState(),
    val recovery: RecoveryUiState = RecoveryUiState(), val reset: ResetPasswordUiState = ResetPasswordUiState()
)
enum class AuthForm { PUBLIC, REGISTER_BRAND, REGISTER_CREATOR, LOGIN, RECOVERY, RESET }
sealed interface AuthEvent { data object GoToLogin : AuthEvent }
