package com.example.collabpro.identity

import com.example.collabpro.auth.*
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.identity.application.auth.*
import com.example.collabpro.features.identity.domain.model.*
import com.example.collabpro.navigation.AppState
import com.example.collabpro.navigation.Route
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AccountMetadataRefreshTest {
    private val repository = FakeIdentity()
    private val store = MemorySessions()
    private val authentication = AuthenticationSession(repository, store, MutableClock())
    @Test fun `refresh preserves JWT expiry and account role while updating authoritative name`() = runTest {
        authentication.signIn("test@example.test", "password123")
        repository.accountResult = ApiResult.Success(AuthFixtures.account.copy(name = "Nombre actualizado"))
        assertTrue(authentication.refreshAccount() is ApiResult.Success)
        assertEquals(AuthFixtures.session.accessToken, store.read()!!.accessToken)
        assertEquals(AuthFixtures.session.expiresAt, store.read()!!.expiresAt)
        assertEquals("Nombre actualizado", (authentication.state.value as SessionState.Authenticated).account.name)
    }
    @Test fun `wrong account role profile status or identity cannot replace verified metadata`() = runTest {
        authentication.signIn("test@example.test", "password123")
        val wrong = listOf(AuthFixtures.account.copy(accountId = UUID.randomUUID()), AuthFixtures.account.copy(profileId = UUID.randomUUID()),
            AuthFixtures.account.copy(accountType = AccountType.BRAND), AuthFixtures.account.copy(status = AccountStatus.DISABLED))
        for (account in wrong) {
            repository.accountResult = ApiResult.Success(account)
            assertEquals(FailureKind.MALFORMED_RESPONSE, (authentication.refreshAccount() as ApiResult.Failure).error.kind)
            assertEquals(AuthFixtures.account, (authentication.state.value as SessionState.Authenticated).account)
        }
    }
    @Test fun `storage failure cannot update UI without persisting account metadata`() = runTest {
        authentication.signIn("test@example.test", "password123")
        repository.accountResult = ApiResult.Success(AuthFixtures.account.copy(name = "Nuevo")); store.saveFailure = true
        assertEquals(FailureKind.STORAGE, (authentication.refreshAccount() as ApiResult.Failure).error.kind)
        assertEquals(AuthFixtures.account, (authentication.state.value as SessionState.Authenticated).account)
    }
    @Test fun `late metadata after logout does not restore access`() = runTest {
        authentication.signIn("test@example.test", "password123")
        val response = CompletableDeferred<ApiResult<Account>>()
        repository.accountCall = { response.await() }
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { authentication.refreshAccount() }
        authentication.signOut(); response.complete(ApiResult.Success(AuthFixtures.account.copy(name = "Nuevo")))
        assertEquals(FailureKind.SESSION_CHANGED, (refresh.await() as ApiResult.Failure).error.kind)
        assertTrue(authentication.state.value is SessionState.SignedOut); assertNull(store.read())
    }
    @Test fun `metadata update preserves private navigation and blocks wrong role profile routes`() {
        val app = AppState(AuthFixtures.account)
        app.go(Route.CREATOR_PROFILE); app.updateAccount(AuthFixtures.account.copy(name = "Nombre actualizado"))
        assertEquals(Route.CREATOR_PROFILE, app.route); assertEquals("Nombre actualizado", app.authenticatedAccount?.name)
        app.go(Route.BRAND_PROFILE); assertEquals(Route.CREATOR_PROFILE, app.route)
        val brand = AppState(AuthFixtures.account.copy(accountType = AccountType.BRAND))
        brand.go(Route.SOCIAL_ACCOUNTS); assertEquals(Route.BRAND_HOME, brand.route)
    }
}
