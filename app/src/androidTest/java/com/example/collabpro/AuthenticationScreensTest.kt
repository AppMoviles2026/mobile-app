package com.example.collabpro

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.collabpro.features.identity.presentation.auth.*
import com.example.collabpro.ui.theme.CollabProTheme
import org.junit.Rule
import org.junit.Test

class AuthenticationScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loginDoesNotHaveSimulatedErrorsOrRoleSelector() {
        compose.setContent { CollabProTheme { LoginScreen(LoginUiState()) } }
        compose.onNodeWithText("Iniciar sesión").assertExists()
        compose.onNodeWithText("Ver credenciales inválidas").assertDoesNotExist()
        compose.onNodeWithText("Empresa").assertDoesNotExist()
        compose.onNodeWithText("Creador").assertDoesNotExist()
    }
    @Test fun busyLoginCannotSubmitTwice() {
        compose.setContent { CollabProTheme { LoginScreen(LoginUiState(submitting = true)) } }
        compose.onNodeWithText("Iniciando sesión…").assertIsNotEnabled()
    }
    @Test fun resetCompletionHasNoPasswordInputs() {
        compose.setContent { CollabProTheme { ResetPasswordScreen(ResetPasswordUiState(completed = true)) } }
        compose.onNodeWithText("Ir a iniciar sesión").assertExists()
        compose.onNodeWithText("Confirmar contraseña").assertDoesNotExist()
    }
}
