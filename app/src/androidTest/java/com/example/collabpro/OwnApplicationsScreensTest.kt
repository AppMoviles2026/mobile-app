package com.example.collabpro

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.applications.ApplicationProposal
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.presentation.applications.*
import com.example.collabpro.ui.theme.CollabProTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class OwnApplicationsScreensTest {
    @get:Rule val compose = createComposeRule()
    private val application = Application(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Campaña real", "Marca real", "Propuesta del servidor",
        ApplicationStatus.PENDING, Instant.parse("2030-01-01T00:00:00Z"), emptySet(), 0)
    @Test fun listFailureIsNotEmptySuccess() {
        compose.setContent { CollabProTheme { OwnApplicationsScreen(OwnApplicationsListUiState(failure = ApiFailure(FailureKind.NETWORK, message = "Sin conexión"))) } }
        compose.onNodeWithText("Sin conexión").assertExists(); compose.onNodeWithText("Todavía no has postulado a una campaña.").assertDoesNotExist()
    }
    @Test fun cancelledDetailIsReadOnlyWithoutFakeResultControls() {
        compose.setContent { CollabProTheme { OwnApplicationDetailScreen(OwnApplicationDetailUiState(application.id, application.copy(status = ApplicationStatus.CANCELLED))) } }
        compose.onNodeWithText("Editar propuesta").assertDoesNotExist(); compose.onNodeWithText("Cancelar postulación").assertDoesNotExist()
        compose.onNodeWithText("Ver resultado: seleccionada").assertDoesNotExist()
    }
    @Test fun conflictKeepsLocalTextAndDisablesBlindSave() {
        compose.setContent { CollabProTheme { OwnApplicationFormScreen(OwnApplicationFormUiState(application.campaignId,
            application = application, proposal = ApplicationProposal("Texto local"), latest = application.copy(message = "Mensaje nuevo", version = 1)), application.submittedAt) } }
        compose.onNodeWithText("Texto local").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Guardar mensaje"))
        compose.onNodeWithText("Guardar mensaje").assertIsNotEnabled()
    }
    @Test fun uncertainWriteDisablesRepeatedCancellation() {
        compose.setContent { CollabProTheme { OwnApplicationDetailScreen(OwnApplicationDetailUiState(application.id, application, pendingOperation = true)) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Cancelar postulación"))
        compose.onNodeWithText("Cancelar postulación").assertIsNotEnabled()
    }
    @Test fun cancellationConfirmationClosesWhenServerVersionChanges() {
        val state = mutableStateOf(OwnApplicationDetailUiState(application.id, application))
        compose.setContent { CollabProTheme { OwnApplicationDetailScreen(state.value) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Cancelar postulación"))
        compose.onNodeWithText("Cancelar postulación").performClick()
        compose.onNodeWithText("¿Cancelar esta postulación?").assertExists()
        compose.runOnIdle { state.value = state.value.copy(application = application.copy(message = "Nueva propuesta", version = 1)) }
        compose.onNodeWithText("¿Cancelar esta postulación?").assertDoesNotExist()
    }
}
