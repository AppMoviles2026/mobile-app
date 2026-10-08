package com.example.collabpro

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.presentation.discovery.*
import com.example.collabpro.ui.theme.CollabProTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class CampaignDiscoveryScreensTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2030-01-01T00:00:00Z")
    @Test fun searchFailureIsNotEmptySuccess() {
        compose.setContent { CollabProTheme { CampaignExploreScreen(CampaignDiscoveryUiState(
            results = DiscoveryPageUiState(failure = ApiFailure(FailureKind.NETWORK, message = "No se pudo consultar")))) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Reintentar búsqueda"))
        compose.onNodeWithText("Sin coincidencias").assertDoesNotExist()
        compose.onAllNodesWithText("No se pudo consultar").onFirst().assertExists()
    }
    @Test fun emptyResultExplainsNoMatches() {
        compose.setContent { CollabProTheme { CampaignExploreScreen(CampaignDiscoveryUiState(results = DiscoveryPageUiState(page = Page(emptyList(), 0, 0, 20)))) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Sin coincidencias"))
        compose.onNodeWithText("Sin coincidencias").assertExists()
    }
    @Test fun closedDetailHasWarningAndNoSimulatedApplicationButton() {
        val id = UUID.randomUUID()
        val detail = CampaignDetails(CampaignSummary(id, UUID.randomUUID(), "Marca real", "Título real", "Moda", "Lima", null,
            now.plusSeconds(60), CampaignStatus.CLOSED, false), "Objetivo real", null, "Audiencia", now.minusSeconds(60), emptyList(), emptyList())
        compose.setContent { CollabProTheme { CreatorCampaignDetailScreen(DiscoveryDetailUiState(id, detail), now) } }
        compose.onNodeWithText("Esta campaña está cerrada y no admite nuevas postulaciones.").assertExists()
        compose.onNodeWithText("Postular a esta campaña").assertDoesNotExist()
        compose.onNodeWithText("Objetivo real").assertExists()
    }
    @Test fun detailNotFoundDoesNotDisplayDemoCampaign() {
        compose.setContent { CollabProTheme { CreatorCampaignDetailScreen(DiscoveryDetailUiState(UUID.randomUUID(),
            failure = ApiFailure(FailureKind.NOT_FOUND, message = "No encontrada")), now) } }
        compose.onNodeWithText("Campaña no encontrada").assertExists()
        compose.onNodeWithText("Sabores que conectan").assertDoesNotExist()
    }
}
