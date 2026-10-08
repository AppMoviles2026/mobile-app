package com.example.collabpro.auth

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.identity.application.auth.*
import com.example.collabpro.features.identity.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationSessionTest {
    private val repository = FakeIdentity()
    private val store = MemorySessions()
    private val clock = MutableClock()
    private val authentication = AuthenticationSession(repository, store, clock)

    @Test fun `login persists a real session and uses the server role`() = runTest {
        authentication.restore()
        assertTrue(authentication.signIn("creator@example.test", "password123") is ApiResult.Success)
        val state = authentication.state.value as SessionState.Authenticated
        assertEquals(AccountType.CREATOR, state.account.accountType)
        assertEquals(AuthFixtures.session, store.read())
    }
    @Test fun `bad credentials never give access`() = runTest {
        authentication.restore()
        repository.loginResult = AuthFixtures.failure(FailureKind.UNAUTHORIZED)
        assertTrue(authentication.signIn("creator@example.test", "wrong") is ApiResult.Failure)
        assertTrue(authentication.state.value is SessionState.SignedOut)
        assertNull(store.read())
    }
    @Test fun `failed secure storage blocks login`() = runTest {
        authentication.restore()
        store.saveFailure = true
        val result = authentication.signIn("creator@example.test", "password123") as ApiResult.Failure
        assertEquals(FailureKind.STORAGE, result.error.kind)
        assertTrue(authentication.state.value is SessionState.SignedOut)
    }
    @Test fun `restoration verifies account name and role instead of trusting cached metadata`() = runTest {
        store.stored = AuthFixtures.session.copy(account = AuthFixtures.account.copy(name = "Old name", accountType = AccountType.BRAND))
        authentication.restore()
        assertEquals(1, repository.accountCount)
        assertEquals(AuthFixtures.account, (authentication.state.value as SessionState.Authenticated).account)
        assertEquals(AuthFixtures.account, store.read()?.account)
    }
    @Test fun `expired stored sessions do not call a protected endpoint`() = runTest {
        store.stored = AuthFixtures.session.copy(expiresAt = clock.instant())
        authentication.restore()
        assertEquals(0, repository.accountCount)
        assertNull(store.read())
        assertTrue(authentication.state.value is SessionState.SignedOut)
    }
    @Test fun `revoked session is cleared`() = runTest {
        store.stored = AuthFixtures.session
        repository.accountResult = AuthFixtures.failure(FailureKind.UNAUTHORIZED)
        authentication.restore()
        assertNull(store.read())
        assertTrue(authentication.state.value is SessionState.SignedOut)
    }
    @Test fun `network loss keeps credentials but cannot unlock private screens`() = runTest {
        store.stored = AuthFixtures.session
        repository.accountResult = AuthFixtures.failure(FailureKind.NETWORK)
        authentication.restore()
        assertTrue(authentication.state.value is SessionState.VerificationFailed)
        assertEquals(AuthFixtures.session, store.read())
        repository.accountResult = ApiResult.Success(AuthFixtures.account)
        authentication.restore()
        assertTrue(authentication.state.value is SessionState.Authenticated)
    }
    @Test fun `logout clears credentials and authenticated state`() = runTest {
        authentication.signIn("creator@example.test", "password123")
        authentication.signOut()
        assertNull(store.read())
        assertTrue(authentication.state.value is SessionState.SignedOut)
    }
    @Test fun `an invalidation from a protected API removes access immediately`() = runTest {
        val watcher = backgroundScope.launch { authentication.observeInvalidations() }
        authentication.signIn("creator@example.test", "password123")
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(authentication.state.value is SessionState.SignedOut)
        watcher.cancel()
    }
    @Test fun `late successful login after logout cannot resurrect an account`() = runTest {
        val response = CompletableDeferred<ApiResult<Session>>()
        repository.loginCall = { response.await() }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { authentication.signIn("creator@example.test", "password123") }
        authentication.signOut()
        response.complete(ApiResult.Success(AuthFixtures.session))
        assertEquals(FailureKind.SESSION_CHANGED, (pending.await() as ApiResult.Failure).error.kind)
        assertNull(store.read())
        assertTrue(authentication.state.value is SessionState.SignedOut)
    }
    @Test fun `late account verification cannot overwrite a newer login`() = runTest {
        store.stored = AuthFixtures.session
        val response = CompletableDeferred<ApiResult<Account>>()
        repository.accountCall = { response.await() }
        val pending = launch(start = CoroutineStart.UNDISPATCHED) { authentication.restore() }
        authentication.signOut()
        val newer = AuthFixtures.session.copy(account = AuthFixtures.account.copy(accountId = java.util.UUID.randomUUID(), name = "New account"), accessToken = "new.jwt.token")
        repository.loginResult = ApiResult.Success(newer)
        authentication.signIn("other@example.test", "password123")
        response.complete(ApiResult.Success(AuthFixtures.account))
        pending.join()
        assertEquals(newer.account, (authentication.state.value as SessionState.Authenticated).account)
        assertEquals(newer, store.read())
    }
    @Test fun `expiration removes credentials even without another HTTP request`() = runTest {
        authentication.signIn("creator@example.test", "password123")
        val expected = authentication.state.value as SessionState.Authenticated
        clock.time = expected.expiresAt
        authentication.expire(expected)
        assertNull(store.read())
        assertTrue(authentication.state.value is SessionState.SignedOut)
    }
    @Test fun `inactive account is not restored`() = runTest {
        store.stored = AuthFixtures.session
        repository.accountResult = ApiResult.Success(AuthFixtures.account.copy(status = AccountStatus.SUSPENDED))
        authentication.restore()
        assertNull(store.read())
        assertTrue(authentication.state.value is SessionState.SignedOut)
    }
    @Test fun `verification of a mismatched account ID is rejected`() = runTest {
        store.stored = AuthFixtures.session
        repository.accountResult = ApiResult.Success(AuthFixtures.account.copy(accountId = java.util.UUID.randomUUID()))
        authentication.restore()
        assertNull(store.read())
        assertTrue(authentication.state.value is SessionState.SignedOut)
    }
}
