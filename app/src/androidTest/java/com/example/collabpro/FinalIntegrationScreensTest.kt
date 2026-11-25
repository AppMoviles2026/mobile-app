package com.example.collabpro

import android.provider.Browser
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.presentation.dashboard.*
import com.example.collabpro.features.identity.domain.model.*
import com.example.collabpro.features.identity.infrastructure.browser.SocialCustomTabs
import com.example.collabpro.features.identity.presentation.*
import com.example.collabpro.features.identity.presentation.profile.*
import com.example.collabpro.ui.theme.CollabProTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class FinalIntegrationScreensTest {
    @get:Rule val compose = createComposeRule()
    private val account = Account(UUID.randomUUID(), UUID.randomUUID(), "Nombre del servidor", AccountType.BRAND, AccountStatus.ACTIVE)

    @Test fun serverNameReplacesPrototypeName() {
        compose.setContent { CollabProTheme { HomeScreen(account) } }
        compose.onNodeWithText("Hola, Nombre del servidor").assertExists()
        compose.onNodeWithText("Maki Studio").assertDoesNotExist()
        compose.onNodeWithText("Camila Rojas").assertDoesNotExist()
    }
    @Test fun businessProfileOffersEditingAfterLoadingTheInitialProfile() {
        val profile = BusinessProfile(account.profileId, account.name, null, null, null, 0)
        compose.setContent { CollabProTheme {
            var state by remember {
                mutableStateOf(BrandProfileUiState(profile, BrandProfileDraft.from(profile)))
            }
            BrandProfileScreen(state, onEdit = { edit -> state = state.copy(draft = state.draft?.edit()) })
        } }
        compose.onNodeWithText("Nombre del servidor").assertExists().performTextReplacement("Nombre actualizado")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Guardar perfil"))
        compose.onNodeWithText("Guardar perfil").assertIsEnabled()
    }
    @Test fun summaryErrorDoesNotPresentZeroOrPendingCounts() {
        compose.setContent { CollabProTheme { ActivityDashboardPanel(ActivityDashboardUiState(type = AccountType.CREATOR,
            failure = ApiFailure(FailureKind.NETWORK, message = "Sin conexión"))) } }
        compose.onNodeWithText("Sin conexión").assertExists()
        compose.onNodeWithText("0").assertDoesNotExist()
        compose.onNodeWithText("Reintentar resumen").assertIsEnabled()
        compose.onNodeWithText("Postulaciones pendientes").assertDoesNotExist()
    }
    @Test fun successfulTotalIsNotLabelledAsActiveOrPending() {
        compose.setContent { CollabProTheme { ActivityDashboardPanel(ActivityDashboardUiState(type = AccountType.CREATOR, total = 13)) } }
        compose.onNodeWithText("Total de postulaciones").assertExists()
        compose.onNodeWithText("13").assertExists()
        compose.onNodeWithText("Incluye todos los estados, también las canceladas.").assertExists()
    }
    @Test fun staleEmptySocialListIsNotClaimedAsConfirmedEmpty() {
        compose.setContent { CollabProTheme { LinkedSocialAccountsScreen(SocialAccountsUiState(loaded = true,
            failure = ApiFailure(FailureKind.NETWORK, message = "Sin conexión"))) } }
        compose.onNodeWithText("Aún no tienes cuentas sociales vinculadas.").assertDoesNotExist()
        compose.onNodeWithText("La lista conserva la última consulta correcta; podría estar desactualizada.").assertExists()
    }
    @Test fun customTabIntentTargetsCompatibleBrowserWithoutApiCredentials() {
        val tab = SocialCustomTabs.intent("test.browser")
        assertEquals("test.browser", tab.intent.`package`)
        assertTrue(tab.intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
        assertEquals(CustomTabsIntent.SHARE_STATE_OFF, tab.intent.getIntExtra(CustomTabsIntent.EXTRA_SHARE_STATE, -1))
        assertFalse(tab.intent.hasExtra(Browser.EXTRA_HEADERS))
    }
}
