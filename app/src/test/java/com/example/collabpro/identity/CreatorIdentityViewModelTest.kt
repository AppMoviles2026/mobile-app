package com.example.collabpro.identity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.collabpro.auth.*
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.identity.application.auth.*
import com.example.collabpro.features.identity.application.usecases.IdentityUseCases
import com.example.collabpro.features.identity.domain.model.*
import com.example.collabpro.features.identity.domain.repositories.IdentityRepository
import com.example.collabpro.features.identity.presentation.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.net.URI
import java.util.UUID
import com.example.collabpro.core.application.security.ExpectedAccount

internal class FakeCreatorIdentity(val auth: FakeIdentity = FakeIdentity()) : IdentityRepository by auth {
    val profile = CreatorProfile(AuthFixtures.account.profileId, "Nombre real", null, null, null, null)
    val attemptId: UUID = UUID.randomUUID()
    var profileResult: ApiResult<CreatorProfile> = ApiResult.Success(profile)
    var updateResult: ApiResult<CreatorProfile> = ApiResult.Success(profile.copy(displayName = "Nuevo nombre"))
    var authorizationResult: ApiResult<SocialAuthorization> = ApiResult.Success(SocialAuthorization(
        URI("https://www.instagram.com/oauth/authorize?state=private-test-state"), attemptId))
    var accountsResult: ApiResult<List<SocialAccount>> = ApiResult.Success(emptyList())
    var statusResult: ApiResult<AuthorizationAttempt> = status(AuthorizationStatus.PENDING)
    var profileCall: suspend () -> ApiResult<CreatorProfile> = { profileResult }
    var updateCall: suspend () -> ApiResult<CreatorProfile> = { updateResult }
    var authorizationCall: suspend () -> ApiResult<SocialAuthorization> = { authorizationResult }
    var statusCall: suspend (UUID) -> ApiResult<AuthorizationAttempt> = { statusResult }
    var accountsCall: suspend () -> ApiResult<List<SocialAccount>> = { accountsResult }
    var lastUpdate: CreatorProfileUpdate? = null
    var profileCount = 0; var updateCount = 0; var startCount = 0; var statusCount = 0; var listCount = 0
    override suspend fun creatorProfile(): ApiResult<CreatorProfile> { profileCount++; return profileCall() }
    override suspend fun updateCreatorProfile(update: CreatorProfileUpdate): ApiResult<CreatorProfile> { updateCount++; lastUpdate = update; return updateCall() }
    override suspend fun authorizeSocialAccount(platform: SocialPlatform): ApiResult<SocialAuthorization> { startCount++; return authorizationCall() }
    override suspend fun socialAccounts(): ApiResult<List<SocialAccount>> { listCount++; return accountsCall() }
    override suspend fun authorizationStatus(id: UUID): ApiResult<AuthorizationAttempt> { statusCount++; return statusCall(id) }
    fun status(value: AuthorizationStatus, error: String? = null, id: UUID = attemptId, platform: SocialPlatform = SocialPlatform.INSTAGRAM) =
        ApiResult.Success(AuthorizationAttempt(id, platform, value, error, AuthFixtures.now.plusSeconds(600)))
}

@OptIn(ExperimentalCoroutinesApi::class)
class CreatorIdentityViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeCreatorIdentity()
    private val sessions = MemorySessions()
    private val clock = MutableClock()
    private val authentication = AuthenticationSession(repository, sessions, clock)
    private val saved = SavedStateHandle()
    private val owners = ViewModelStore()
    private lateinit var vm: CreatorIdentityViewModel
    @Before fun setup() {
        vm = CreatorIdentityViewModel(IdentityUseCases(repository, sessions), authentication, saved, clock)
        owners.put("creator", vm)
    }
    @After fun cleanup() { owners.clear() }
    private suspend fun login() { authentication.signIn("test@example.test", "password123") }
    private fun callback(id: UUID = repository.attemptId) = "collabpro://social-authorization-completed?authorizationId=$id"

    @Test fun `duplicate return preserves terminal state and reloads accounts only once`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.statusResult = repository.status(AuthorizationStatus.SUCCEEDED)
        vm.receiveAuthorizationReturn(callback()); runCurrent(); vm.receiveAuthorizationReturn(callback()); vm.onResume(); runCurrent()
        assertEquals(1, repository.statusCount); assertEquals(1, repository.listCount)
        assertEquals(AuthorizationStatus.SUCCEEDED, vm.ui.value.social.link!!.status)
    }
    @Test fun `duplicate delivery cannot cancel or restart an in flight status read`() = runTest(main.dispatcher) {
        login(); runCurrent(); val result = CompletableDeferred<ApiResult<AuthorizationAttempt>>()
        repository.statusCall = { result.await() }
        vm.receiveAuthorizationReturn(callback()); runCurrent(); vm.receiveAuthorizationReturn(callback()); vm.onResume(); runCurrent()
        assertEquals(1, repository.statusCount); assertTrue(vm.ui.value.social.link!!.checking)
        result.complete(repository.status(AuthorizationStatus.SUCCEEDED)); runCurrent()
        assertEquals(AuthorizationStatus.SUCCEEDED, vm.ui.value.social.link!!.status); assertEquals(1, repository.listCount)
    }
    @Test fun `failed read is retryable manually not by replaying the same deep link`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.statusResult = AuthFixtures.failure(FailureKind.NETWORK)
        vm.receiveAuthorizationReturn(callback()); runCurrent(); vm.receiveAuthorizationReturn(callback()); runCurrent()
        assertEquals(1, repository.statusCount)
        repository.statusResult = repository.status(AuthorizationStatus.SUCCEEDED); vm.checkAuthorization(); runCurrent()
        assertEquals(2, repository.statusCount); assertEquals(AuthorizationStatus.SUCCEEDED, vm.ui.value.social.link!!.status)
    }
    @Test fun `protected profile and social requests retain the initiating account context`() = runTest(main.dispatcher) {
        login(); runCurrent(); val contexts = mutableListOf<ExpectedAccount?>()
        repository.profileCall = { contexts.add(currentCoroutineContext()[ExpectedAccount]); repository.profileResult }
        repository.accountsCall = { contexts.add(currentCoroutineContext()[ExpectedAccount]); repository.accountsResult }
        repository.authorizationCall = { contexts.add(currentCoroutineContext()[ExpectedAccount]); repository.authorizationResult }
        repository.statusCall = { contexts.add(currentCoroutineContext()[ExpectedAccount]); repository.status(AuthorizationStatus.EXPIRED) }
        vm.loadProfile(); vm.loadSocialAccounts(); runCurrent(); vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent()
        vm.checkAuthorization(); runCurrent()
        assertTrue(contexts.size >= 4)
        assertTrue(contexts.all { it?.accountId == AuthFixtures.account.accountId && it.expiresAt == AuthFixtures.session.expiresAt })
        assertEquals(AuthFixtures.account.accountId, vm.ui.value.ownerId); assertEquals(AuthFixtures.session.expiresAt, vm.ui.value.expiresAt)
    }
    @Test fun `expired session cannot request or reopen social browser before observer clears it`() = runTest(main.dispatcher) {
        login(); runCurrent(); val event = async { vm.browserEvents.first() }; vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent()
        val request = event.await(); clock.time = AuthFixtures.session.expiresAt
        vm.loadSocialAccounts(); vm.checkAuthorization(); vm.loadProfile(); vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent()
        assertFalse(vm.canOpenBrowser(request)); assertEquals(0, repository.listCount); assertEquals(0, repository.statusCount); assertEquals(0, repository.profileCount)
    }
    @Test fun `old browser event cannot be used by a new login of the same account`() = runTest(main.dispatcher) {
        login(); runCurrent(); val event = async { vm.browserEvents.first() }; vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent()
        val old = event.await(); authentication.signOut(); runCurrent()
        repository.auth.loginResult = ApiResult.Success(AuthFixtures.session.copy(accessToken = "new.jwt.token", expiresAt = AuthFixtures.session.expiresAt.plusSeconds(30)))
        login(); runCurrent(); vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent()
        assertFalse(vm.canOpenBrowser(old)); assertEquals(2, repository.startCount)
    }
    @Test fun `duplicate account UUIDs are rejected without rendering invalid lazy keys`() = runTest(main.dispatcher) {
        login(); runCurrent(); val account = SocialAccount(UUID.randomUUID(), SocialPlatform.INSTAGRAM, "real", SocialAccountStatus.ACTIVE)
        repository.accountsResult = ApiResult.Success(listOf(account, account)); vm.loadSocialAccounts(); runCurrent()
        assertEquals(FailureKind.MALFORMED_RESPONSE, vm.ui.value.social.failure!!.kind)
        assertFalse(vm.ui.value.social.loaded); assertTrue(vm.ui.value.social.accounts.isEmpty())
    }
    @Test fun `failed refresh retains last confirmed accounts and does not fabricate new ones`() = runTest(main.dispatcher) {
        login(); runCurrent(); val account = SocialAccount(UUID.randomUUID(), SocialPlatform.TIKTOK, "real", SocialAccountStatus.ACTIVE)
        repository.accountsResult = ApiResult.Success(listOf(account)); vm.loadSocialAccounts(); runCurrent()
        repository.accountsResult = AuthFixtures.failure(FailureKind.NETWORK); vm.loadSocialAccounts(); runCurrent()
        assertEquals(listOf(account), vm.ui.value.social.accounts); assertNotNull(vm.ui.value.social.failure)
    }

    @Test fun `unverified and brand sessions never request creator data`() = runTest(main.dispatcher) {
        runCurrent(); vm.loadProfile(); vm.loadSocialAccounts(); vm.startAuthorization(SocialPlatform.INSTAGRAM)
        runCurrent(); assertEquals(0, repository.profileCount); assertEquals(0, repository.listCount)
        repository.auth.loginResult = ApiResult.Success(AuthFixtures.session.copy(account = AuthFixtures.account.copy(accountType = AccountType.BRAND)))
        login(); runCurrent(); vm.loadProfile(); vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent()
        assertEquals(0, repository.profileCount); assertEquals(0, repository.startCount)
    }
    @Test fun `profile reads only server fields without preview data`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.loadProfile(); assertTrue(vm.ui.value.profile.loading); runCurrent()
        assertEquals(repository.profile, vm.ui.value.profile.profile)
        assertEquals("", vm.ui.value.profile.draft.audienceDescription)
        assertFalse(vm.ui.value.profile.dirty)
        vm.loadProfile(); runCurrent(); assertEquals(1, repository.profileCount)
    }
    @Test fun `profile failure is not replaced by empty or preview data and retry works`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.profileResult = AuthFixtures.failure(FailureKind.NETWORK)
        vm.loadProfile(); runCurrent(); assertNull(vm.ui.value.profile.profile); assertEquals(FailureKind.NETWORK, vm.ui.value.profile.failure?.kind)
        repository.profileResult = ApiResult.Success(repository.profile); vm.loadProfile(); runCurrent()
        assertNotNull(vm.ui.value.profile.profile); assertNull(vm.ui.value.profile.failure)
    }
    @Test fun `profile of another account is rejected`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.profileResult = ApiResult.Success(repository.profile.copy(profileId = UUID.randomUUID()))
        vm.loadProfile(); runCurrent(); assertNull(vm.ui.value.profile.profile)
        assertEquals(FailureKind.MALFORMED_RESPONSE, vm.ui.value.profile.failure?.kind)
    }
    @Test fun `profile save normalizes all fields uses PUT response and refreshes session name`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.loadProfile(); runCurrent()
        vm.editProfile { copy(displayName = " Nuevo nombre ", biography = " Bio ", niche = " Cocina ", audienceDescription = " Jóvenes ", location = " Lima ") }
        repository.auth.accountResult = ApiResult.Success(AuthFixtures.account.copy(name = "Nuevo nombre"))
        vm.saveProfile(); vm.saveProfile(); assertTrue(vm.ui.value.profile.saving); runCurrent()
        assertEquals(1, repository.updateCount)
        assertEquals(CreatorProfileUpdate("Nuevo nombre", "Bio", "Cocina", "Jóvenes", "Lima"), repository.lastUpdate)
        assertEquals(repository.updateResult, ApiResult.Success(vm.ui.value.profile.profile!!))
        assertFalse(vm.ui.value.profile.dirty); assertFalse(vm.ui.value.profile.saving)
        assertEquals("Nuevo nombre", (authentication.state.value as SessionState.Authenticated).account.name)
        assertEquals("Nuevo nombre", sessions.read()?.account?.name)
    }
    @Test fun `invalid profile does not call server and preserves draft and field errors`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.loadProfile(); runCurrent(); vm.editProfile { copy(displayName = " ") }
        vm.saveProfile(); runCurrent(); assertEquals(0, repository.updateCount)
        assertEquals(" ", vm.ui.value.profile.draft.displayName); assertTrue(vm.ui.value.profile.failure!!.fieldErrors.containsKey("displayName"))
    }
    @Test fun `failed save retains edits and does not display saved success`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.loadProfile(); runCurrent(); vm.editProfile { copy(displayName = "Nuevo") }
        repository.updateResult = ApiResult.Failure(ApiFailure(FailureKind.VALIDATION, message = "Nombre inválido", fieldErrors = mapOf("displayName" to "Revisa el nombre")))
        vm.saveProfile(); runCurrent(); assertTrue(vm.ui.value.profile.dirty); assertNull(vm.ui.value.profile.notice)
        assertEquals("Nuevo", vm.ui.value.profile.draft.displayName); assertEquals("Revisa el nombre", vm.ui.value.profile.failure?.fieldErrors?.get("displayName"))
    }
    @Test fun `account metadata refresh failure cannot roll back confirmed profile save`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.loadProfile(); runCurrent(); vm.editProfile { copy(displayName = "Nuevo nombre") }
        repository.auth.accountResult = AuthFixtures.failure(FailureKind.NETWORK)
        vm.saveProfile(); runCurrent(); assertEquals("Nuevo nombre", vm.ui.value.profile.profile?.displayName)
        assertTrue(vm.ui.value.profile.accountRefreshFailed); assertTrue(vm.ui.value.profile.notice!!.startsWith("Perfil guardado"))
        repository.auth.accountResult = ApiResult.Success(AuthFixtures.account.copy(name = "Nuevo nombre"))
        vm.refreshAccountName(); runCurrent(); assertFalse(vm.ui.value.profile.accountRefreshFailed)
    }
    @Test fun `discard and force reload restore server profile`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.loadProfile(); runCurrent(); vm.editProfile { copy(niche = "Otra") }
        vm.discardChanges(); assertFalse(vm.ui.value.profile.dirty)
        repository.profileResult = ApiResult.Success(repository.profile.copy(niche = "Servidor"))
        vm.loadProfile(force = true); runCurrent(); assertEquals("Servidor", vm.ui.value.profile.draft.niche)
    }
    @Test fun `logout clears drafts and ignores an uncancellable late profile response`() = runTest(main.dispatcher) {
        login(); runCurrent(); val response = CompletableDeferred<ApiResult<CreatorProfile>>()
        repository.profileCall = { withContext(NonCancellable) { response.await() } }
        vm.loadProfile(); runCurrent(); authentication.signOut(); runCurrent()
        response.complete(ApiResult.Success(repository.profile)); runCurrent()
        assertEquals(CreatorIdentityUiState(), vm.ui.value)
    }
    @Test fun `list supports multiple accounts per platform and revoked states`() = runTest(main.dispatcher) {
        login(); runCurrent(); val accounts = listOf(
            SocialAccount(UUID.randomUUID(), SocialPlatform.INSTAGRAM, "uno", SocialAccountStatus.ACTIVE),
            SocialAccount(UUID.randomUUID(), SocialPlatform.INSTAGRAM, "dos", SocialAccountStatus.REVOKED))
        repository.accountsResult = ApiResult.Success(accounts); vm.loadSocialAccounts(); runCurrent()
        assertEquals(accounts, vm.ui.value.social.accounts); assertTrue(vm.ui.value.social.loaded)
    }
    @Test fun `list failure is not an empty successful list`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.accountsResult = AuthFixtures.failure(FailureKind.NETWORK)
        vm.loadSocialAccounts(); runCurrent(); assertFalse(vm.ui.value.social.loaded)
        assertEquals(FailureKind.NETWORK, vm.ui.value.social.failure?.kind)
    }
    @Test fun `OAuth starts once persists only opaque ids and emits one browser event`() = runTest(main.dispatcher) {
        login(); runCurrent(); val event = async { vm.browserEvents.first() }
        vm.startAuthorization(SocialPlatform.INSTAGRAM); vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent()
        assertEquals(1, repository.startCount); assertTrue(vm.canOpenBrowser(event.await()))
        assertEquals(repository.attemptId.toString(), saved.get<String>(CreatorIdentityViewModel.PENDING_ID))
        assertEquals(setOf(CreatorIdentityViewModel.PENDING_OWNER, CreatorIdentityViewModel.PENDING_ID, CreatorIdentityViewModel.PENDING_PLATFORM), saved.keys())
        assertFalse(event.await().toString().contains("private-test-state"))
        assertEquals(AuthorizationStatus.PENDING, vm.ui.value.social.link?.status)
    }
    @Test fun `provider configuration failure emits no pending success`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.authorizationResult = ApiResult.Failure(ApiFailure(FailureKind.PROVIDER_UNAVAILABLE, "PROVIDER_NOT_CONFIGURED", "No configurado"))
        vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent()
        assertNull(vm.ui.value.social.link); assertNull(vm.ui.value.social.starting)
        assertEquals("PROVIDER_NOT_CONFIGURED", vm.ui.value.social.failure?.code); assertTrue(saved.keys().isEmpty())
    }
    @Test fun `untrusted browser url cannot be opened`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.authorizationResult = ApiResult.Success(SocialAuthorization(URI("https://evil.example/oauth"), repository.attemptId))
        vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent()
        assertNull(vm.ui.value.social.link); assertEquals(FailureKind.MALFORMED_RESPONSE, vm.ui.value.social.failure?.kind)
    }
    @Test fun `return checks backend success and reloads linked accounts`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent()
        repository.statusResult = repository.status(AuthorizationStatus.SUCCEEDED)
        repository.accountsResult = ApiResult.Success(listOf(SocialAccount(UUID.randomUUID(), SocialPlatform.INSTAGRAM, "real", SocialAccountStatus.ACTIVE)))
        vm.receiveAuthorizationReturn(callback()); assertEquals(AuthorizationStatus.PENDING, vm.ui.value.social.link?.status)
        runCurrent(); assertEquals(AuthorizationStatus.SUCCEEDED, vm.ui.value.social.link?.status)
        assertEquals("real", vm.ui.value.social.accounts.single().username); assertTrue(saved.keys().isEmpty()); assertTrue(vm.ui.value.showSocial)
    }
    @Test fun `denied permission cannot add a linked account`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.statusResult = repository.status(AuthorizationStatus.FAILED, "AUTHORIZATION_DENIED")
        vm.receiveAuthorizationReturn(callback()); runCurrent()
        assertEquals(AuthorizationStatus.FAILED, vm.ui.value.social.link?.status)
        assertTrue(vm.ui.value.social.link!!.message!!.contains("No concediste")); assertTrue(vm.ui.value.social.accounts.isEmpty())
    }
    @Test fun `duplicate account result refreshes list but does not add mock duplicates`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.statusResult = repository.status(AuthorizationStatus.FAILED, "SOCIAL_ACCOUNT_ALREADY_LINKED")
        repository.accountsResult = ApiResult.Success(listOf(SocialAccount(UUID.randomUUID(), SocialPlatform.INSTAGRAM, "real", SocialAccountStatus.ACTIVE)))
        vm.receiveAuthorizationReturn(callback()); runCurrent(); assertEquals(1, vm.ui.value.social.accounts.size)
        assertTrue(vm.ui.value.social.link!!.message!!.contains("duplicado"))
    }
    @Test fun `expired authorization permits a new attempt`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.statusResult = repository.status(AuthorizationStatus.EXPIRED)
        vm.receiveAuthorizationReturn(callback()); runCurrent(); assertEquals(AuthorizationStatus.EXPIRED, vm.ui.value.social.link?.status)
        vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent(); assertEquals(1, repository.startCount)
    }
    @Test fun `browser cancellation stays pending with bounded polling and manual check`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent(); vm.onResume(); vm.onResume()
        advanceUntilIdle(); assertEquals(3, repository.statusCount); assertEquals(AuthorizationStatus.PENDING, vm.ui.value.social.link?.status)
        assertFalse(vm.ui.value.social.link!!.checking); assertTrue(vm.ui.value.social.accounts.isEmpty())
        repository.statusResult = repository.status(AuthorizationStatus.SUCCEEDED); vm.checkAuthorization(); runCurrent()
        assertEquals(AuthorizationStatus.SUCCEEDED, vm.ui.value.social.link?.status)
    }
    @Test fun `status network failure keeps a retryable attempt not success`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.statusResult = AuthFixtures.failure(FailureKind.NETWORK)
        vm.receiveAuthorizationReturn(callback()); runCurrent()
        assertEquals(AuthorizationStatus.PENDING, vm.ui.value.social.link?.status)
        assertEquals(FailureKind.NETWORK, vm.ui.value.social.link?.failure?.kind); assertEquals(0, repository.listCount)
    }
    @Test fun `malformed and mismatched returns do not query a different authorization`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent()
        vm.receiveAuthorizationReturn("collabpro://social-authorization-completed?authorizationId=invalid"); runCurrent()
        assertEquals("INVALID_SOCIAL_RETURN", vm.ui.value.social.failure?.code)
        vm.receiveAuthorizationReturn(callback(UUID.randomUUID())); runCurrent()
        assertEquals("MISMATCHED_SOCIAL_RETURN", vm.ui.value.social.failure?.code)
        assertEquals(repository.attemptId, vm.ui.value.social.link?.authorizationId); assertEquals(0, repository.statusCount)
    }
    @Test fun `returned platform mismatch is a malformed response not success`() = runTest(main.dispatcher) {
        login(); runCurrent(); vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent()
        repository.statusResult = repository.status(AuthorizationStatus.SUCCEEDED, platform = SocialPlatform.TIKTOK)
        vm.receiveAuthorizationReturn(callback()); runCurrent()
        assertEquals(AuthorizationStatus.PENDING, vm.ui.value.social.link?.status)
        assertEquals(FailureKind.MALFORMED_RESPONSE, vm.ui.value.social.link?.failure?.kind)
    }
    @Test fun `cold return waits for verified login and uses server ownership without saved attempt`() = runTest(main.dispatcher) {
        runCurrent(); vm.receiveAuthorizationReturn(callback()); assertTrue(vm.ui.value.awaitingLogin)
        assertEquals(0, repository.statusCount); repository.statusResult = repository.status(AuthorizationStatus.SUCCEEDED)
        login(); runCurrent(); assertEquals(1, repository.statusCount); assertTrue(vm.ui.value.showSocial)
        assertFalse(vm.ui.value.awaitingLogin); assertEquals(AuthorizationStatus.SUCCEEDED, vm.ui.value.social.link?.status)
    }
    @Test fun `return owned by another account cannot assert success`() = runTest(main.dispatcher) {
        login(); runCurrent(); repository.statusResult = ApiResult.Failure(ApiFailure(FailureKind.NOT_FOUND, "AUTHORIZATION_NOT_FOUND", "No pertenece"))
        vm.receiveAuthorizationReturn(callback()); runCurrent(); assertEquals(AuthorizationStatus.PENDING, vm.ui.value.social.link?.status)
        assertEquals("AUTHORIZATION_NOT_FOUND", vm.ui.value.social.link?.failure?.code)
    }
    @Test fun `pending saved state is restored only for the same verified owner`() = runTest(main.dispatcher) {
        saved[CreatorIdentityViewModel.PENDING_OWNER] = AuthFixtures.account.accountId.toString()
        saved[CreatorIdentityViewModel.PENDING_ID] = repository.attemptId.toString()
        saved[CreatorIdentityViewModel.PENDING_PLATFORM] = SocialPlatform.INSTAGRAM.name
        repository.statusResult = repository.status(AuthorizationStatus.EXPIRED)
        login(); runCurrent(); assertEquals(AuthorizationStatus.EXPIRED, vm.ui.value.social.link?.status)
        assertEquals(1, repository.statusCount)
    }
    @Test fun `saved pending authorization of another user is discarded`() = runTest(main.dispatcher) {
        saved[CreatorIdentityViewModel.PENDING_OWNER] = UUID.randomUUID().toString()
        saved[CreatorIdentityViewModel.PENDING_ID] = repository.attemptId.toString()
        saved[CreatorIdentityViewModel.PENDING_PLATFORM] = SocialPlatform.INSTAGRAM.name
        login(); runCurrent(); assertNull(vm.ui.value.social.link); assertEquals(0, repository.statusCount); assertTrue(saved.keys().isEmpty())
    }
    @Test fun `logout drops browser events and pending state`() = runTest(main.dispatcher) {
        login(); runCurrent(); val event = async { vm.browserEvents.first() }
        vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent(); val request = event.await()
        authentication.signOut(); runCurrent(); assertFalse(vm.canOpenBrowser(request)); assertNull(vm.ui.value.social.link); assertTrue(saved.keys().isEmpty())
    }
    @Test fun `late start cannot open browser after logout`() = runTest(main.dispatcher) {
        login(); runCurrent(); val response = CompletableDeferred<ApiResult<SocialAuthorization>>()
        repository.authorizationCall = { withContext(NonCancellable) { response.await() } }
        vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent(); authentication.signOut(); runCurrent()
        response.complete(repository.authorizationResult); runCurrent(); assertNull(vm.ui.value.social.link); assertTrue(saved.keys().isEmpty())
    }
    @Test fun `browser unavailable is visible and forgetting does not fabricate cancellation`() = runTest(main.dispatcher) {
        login(); runCurrent(); val event = async { vm.browserEvents.first() }
        vm.startAuthorization(SocialPlatform.INSTAGRAM); runCurrent(); vm.browserUnavailable(event.await())
        assertEquals("BROWSER_UNAVAILABLE", vm.ui.value.social.link?.failure?.code)
        vm.forgetAttempt(); runCurrent(); assertNull(vm.ui.value.social.link); assertEquals(0, repository.statusCount); assertTrue(saved.keys().isEmpty())
    }
}
