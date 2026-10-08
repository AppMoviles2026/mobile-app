package com.example.collabpro

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.drafts.*
import com.example.collabpro.features.campaign.presentation.manage.*
import com.example.collabpro.ui.theme.CollabProTheme
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class CampaignPreparationScreensTest {
    @get:Rule val compose = createComposeRule()
    @Test fun createdMetadataIsReadOnly() {
        compose.setContent { CollabProTheme { CampaignBasicsScreen(CampaignEditorUiState(
            draft = CampaignDraft(serverId = UUID.randomUUID(), basics = CampaignBasics(title = "Campaña real")), restoring = false)) } }
        compose.onNodeWithText("Campaña real").assertIsNotEnabled()
    }
    @Test fun listErrorDoesNotClaimNoCampaigns() {
        compose.setContent { CollabProTheme { OwnCampaignsScreen(OwnCampaignsUiState(failure = ApiFailure(FailureKind.NETWORK, message = "Sin conexión")), CampaignEditorUiState()) } }
        compose.onNodeWithText("Sin conexión").assertExists(); compose.onNodeWithText("No hay campañas en esta página.").assertDoesNotExist()
    }
    @Test fun conditionsHaveNoSimulatedPublicationControls() {
        compose.setContent { CollabProTheme { CampaignConditionsScreen(CampaignEditorUiState(draft = CampaignDraft(step = 2), restoring = false)) } }
        compose.onNodeWithText("Ver condiciones incompatibles").assertDoesNotExist()
        compose.onNodeWithText("Cierre de postulaciones").assertExists()
    }
}
