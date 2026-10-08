package com.example.collabpro

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.identity.domain.model.*
import com.example.collabpro.features.identity.presentation.profile.*
import com.example.collabpro.ui.theme.CollabProTheme
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class CreatorIdentityScreensTest {
    @get:Rule val compose = createComposeRule()
    @Test fun linkedScreensContainNoSimulatedAuthorization() {
        compose.setContent { CollabProTheme { LinkedSocialAccountsScreen(SocialAccountsUiState(loaded = true)) } }
        compose.onNodeWithText("Simular autorización").assertDoesNotExist()
        compose.onNodeWithText("Simular autorización rechazada").assertDoesNotExist()
        compose.onNodeWithText("Vincular Instagram").performScrollTo().assertIsEnabled()
    }
    @Test fun listFailureDoesNotClaimThereAreNoLinkedAccounts() {
        compose.setContent { CollabProTheme { LinkedSocialAccountsScreen(SocialAccountsUiState(
            failure = ApiFailure(FailureKind.NETWORK, message = "Sin conexión"))) } }
        compose.onNodeWithText("Sin conexión").assertExists()
        compose.onNodeWithText("Aún no tienes cuentas sociales vinculadas.").assertDoesNotExist()
    }
    @Test fun pendingAuthorizationCannotStartAnotherLink() {
        compose.setContent { CollabProTheme { LinkedSocialAccountsScreen(SocialAccountsUiState(loaded = true,
            link = SocialLinkUiState(UUID.randomUUID(), SocialPlatform.INSTAGRAM))) } }
        compose.onNodeWithText("Vincular Instagram").performScrollTo().assertIsNotEnabled()
    }
    @Test fun creatorProfileDisplaysEditableAudienceAndNoDemoLocation() {
        val profile = CreatorProfile(UUID.randomUUID(), "Persona real", null, null, "Audiencia real", "Cusco")
        compose.setContent { CollabProTheme { CreatorProfileScreen(CreatorProfileUiState(profile, ProfileDraft.from(profile))) } }
        compose.onNodeWithText("Audiencia real").performScrollTo().assertExists()
        compose.onNodeWithText("Cusco").performScrollTo().assertExists()
    }
}
