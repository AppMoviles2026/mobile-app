package com.example.collabpro.features.identity.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.core.domain.ApiFailure
import com.example.collabpro.features.identity.domain.model.AccountType

@Composable
private fun AuthField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean,
    failure: ApiFailure?, field: String, password: Boolean = false, email: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    val error = failure?.fieldErrors?.get(field)
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        enabled = enabled, isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false,
            keyboardType = if (password) KeyboardType.Password else if (email) KeyboardType.Email else KeyboardType.Text),
        trailingIcon = if (password) { { TextButton(onClick = { visible = !visible }, enabled = enabled) {
            Text(if (visible) "Ocultar" else "Ver")
        } } } else null)
}

@Composable
private fun Feedback(failure: ApiFailure?, submitting: Boolean) {
    if (failure != null) Notice(failure.message)
    if (submitting) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text("Conectando con el servidor…")
    }
}

@Composable
fun RegisterScreen(state: RegistrationUiState, onName: (String) -> Unit = {}, onEmail: (String) -> Unit = {},
    onPassword: (String) -> Unit = {}, onSubmit: () -> Unit = {}, onLogin: () -> Unit = {}, onBack: () -> Unit = {}) {
    val brand = state.type == AccountType.BRAND
    Page(if (brand) "Crea tu cuenta empresa" else "Crea tu cuenta creador",
        subtitle = "Registra tus datos. Después podrás iniciar sesión y completar tu perfil.", onBack = onBack) {
        item { AuthField(if (brand) "Nombre de empresa" else "Nombre público", state.name, onName, !state.submitting,
            state.failure, if (brand) "businessName" else "displayName") }
        item { AuthField("Correo electrónico", state.email, onEmail, !state.submitting, state.failure, "email", email = true) }
        item { AuthField("Contraseña", state.password, onPassword, !state.submitting, state.failure, "password", password = true) }
        item { Text("Usa entre 8 y 128 caracteres. Tu contraseña distingue espacios y mayúsculas.") }
        item { Feedback(state.failure, state.submitting) }
        item { Action(if (state.submitting) "Creando cuenta…" else "Crear cuenta", onSubmit, enabled = !state.submitting) }
        item { Action("Ya tengo cuenta", onLogin, secondary = true) }
    }
}

@Composable
fun LoginScreen(state: LoginUiState, onEmail: (String) -> Unit = {}, onPassword: (String) -> Unit = {},
    onSubmit: () -> Unit = {}, onRecovery: () -> Unit = {}, onRegister: () -> Unit = {}, onBack: () -> Unit = {}) {
    Page("Bienvenido de nuevo", subtitle = "Accede con tu cuenta. El servidor determinará tu tipo de perfil.", onBack = onBack) {
        if (state.notice != null) item { Notice(state.notice) }
        item { AuthField("Correo electrónico", state.email, onEmail, !state.submitting, state.failure, "email", email = true) }
        item { AuthField("Contraseña", state.password, onPassword, !state.submitting, state.failure, "password", password = true) }
        item { Feedback(state.failure, state.submitting) }
        item { Action(if (state.submitting) "Iniciando sesión…" else "Iniciar sesión", onSubmit, enabled = !state.submitting) }
        item { TextButton(onClick = onRecovery) { Text("Olvidé mi contraseña") } }
        item { Action("Crear una cuenta", onRegister, secondary = true) }
    }
}

@Composable
fun RecoverScreen(state: RecoveryUiState, onEmail: (String) -> Unit = {}, onSubmit: () -> Unit = {},
    onLogin: () -> Unit = {}, onBack: () -> Unit = {}) {
    Page("Recuperar acceso", subtitle = "Ingresa tu correo. Si existe una cuenta, recibirás un enlace para cambiar tu contraseña.", onBack = onBack) {
        item { AuthField("Correo electrónico", state.email, onEmail, !state.submitting, state.failure, "email", email = true) }
        item { Feedback(state.failure, state.submitting) }
        if (state.message != null) item { Notice(state.message + " Abre el enlace del correo en este teléfono. Usa el más reciente.") }
        item { Action(if (state.submitting) "Enviando solicitud…" else if (state.message != null) "Enviar nuevamente" else "Enviar enlace", onSubmit, enabled = !state.submitting) }
        item { Action("Volver a iniciar sesión", onLogin, secondary = true) }
    }
}

@Composable
fun ResetPasswordScreen(state: ResetPasswordUiState, onPassword: (String) -> Unit = {},
    onConfirmation: (String) -> Unit = {}, onSubmit: () -> Unit = {}, onLogin: () -> Unit = {},
    onRecovery: () -> Unit = {}, onBack: () -> Unit = {}) {
    Page("Nueva contraseña", subtitle = "Restablece el acceso a tu cuenta con el enlace recibido por correo.", onBack = onBack) {
        when {
            state.completed -> {
                item { Notice("Contraseña actualizada. Tus sesiones anteriores fueron revocadas; inicia sesión de nuevo.") }
                item { Action("Ir a iniciar sesión", onLogin) }
            }
            !state.linkValid -> {
                item { Notice("El enlace está incompleto o no es válido. Solicita un nuevo correo de recuperación.") }
                item { Action("Solicitar otro enlace", onRecovery) }
            }
            else -> {
                item { AuthField("Nueva contraseña", state.password, onPassword, !state.submitting, state.failure, "newPassword", password = true) }
                item { AuthField("Confirmar contraseña", state.confirmation, onConfirmation, !state.submitting, state.failure, "confirmation", password = true) }
                item { Text("Usa entre 8 y 128 caracteres.") }
                item { Feedback(state.failure, state.submitting) }
                item { Action(if (state.submitting) "Actualizando…" else "Actualizar contraseña", onSubmit, enabled = !state.submitting) }
                item { Action("Solicitar otro enlace", onRecovery, secondary = true) }
                item { Action("Ir a iniciar sesión", onLogin, secondary = true) }
            }
        }
    }
}

@Composable
fun SessionGateScreen(failure: ApiFailure? = null, onRetry: () -> Unit = {}, onSignOut: () -> Unit = {}) {
    Page("Verificar sesión", subtitle = "Comprobamos tu cuenta antes de abrir tu espacio privado.") {
        if (failure == null) item { CircularProgressIndicator() }
        else {
            item { Notice(failure.message + " No se ha dado acceso sin verificar tu sesión.") }
            item { Action("Reintentar", onRetry) }
            item { Action("Cerrar sesión e ir al login", onSignOut, secondary = true) }
        }
    }
}
