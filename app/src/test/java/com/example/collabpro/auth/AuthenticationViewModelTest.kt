package com.example.collabpro.auth

import androidx.lifecycle.ViewModelStore
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.identity.application.auth.*
import com.example.collabpro.features.identity.application.usecases.IdentityUseCases
import com.example.collabpro.features.identity.presentation.auth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    val dispatcher = StandardTestDispatcher()
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeIdentity()
    private val sessions = MemorySessions()
    private val clock = MutableClock()
    private lateinit var vm: AuthenticationViewModel
    private val owners = ViewModelStore()
    @Before fun setup() {
        vm = AuthenticationViewModel(IdentityUseCases(repository, sessions), AuthenticationSession(repository, sessions, clock), clock)
        owners.put("auth", vm)
    }
    @After fun cleanup() { owners.clear() }

    private fun registration(form: AuthForm) {
        vm.enterForm(form)
        vm.registrationName("  Nombre  ")
        vm.registrationEmail("  test@example.test  ")
        vm.registrationPassword("  password123  ")
    }
    private fun login() {
        vm.enterForm(AuthForm.LOGIN)
        vm.loginEmail("  test@example.test  ")
        vm.loginPassword("  password123  ")
    }

    @Test fun `brand registration calls the backend and navigates to login without a session`() = runTest(main.dispatcher) {
        runCurrent()
        registration(AuthForm.REGISTER_BRAND)
        val event = async { vm.events.first() }
        vm.register()
        assertTrue(vm.ui.value.registration.submitting)
        runCurrent()
        assertEquals(AuthEvent.GoToLogin, event.await())
        assertEquals(1, repository.brandCount)
        assertEquals(0, repository.creatorCount)
        assertEquals("Nombre", repository.lastName)
        assertEquals("test@example.test", repository.lastEmail)
        assertEquals("  password123  ", repository.lastPassword)
        assertEquals("test@example.test", vm.ui.value.login.email)
        assertEquals("", vm.ui.value.registration.password)
        assertNull(sessions.read())
    }
    @Test fun `creator registration uses its own endpoint`() = runTest(main.dispatcher) {
        runCurrent()
        registration(AuthForm.REGISTER_CREATOR)
        vm.register()
        runCurrent()
        assertEquals(1, repository.creatorCount)
        assertEquals(0, repository.brandCount)
    }
    @Test fun `validation prevents malformed registration requests`() = runTest(main.dispatcher) {
        runCurrent()
        vm.enterForm(AuthForm.REGISTER_CREATOR)
        vm.register()
        runCurrent()
        assertEquals(0, repository.creatorCount)
        assertTrue(vm.ui.value.registration.failure!!.fieldErrors.containsKey("displayName"))
    }
    @Test fun `duplicate email preserves form and shows the real conflict`() = runTest(main.dispatcher) {
        runCurrent()
        repository.registrationResult = AuthFixtures.failure(FailureKind.CONFLICT)
        registration(AuthForm.REGISTER_CREATOR)
        vm.register()
        runCurrent()
        assertEquals(FailureKind.CONFLICT, vm.ui.value.registration.failure!!.kind)
        assertEquals("  test@example.test  ", vm.ui.value.registration.email)
        assertTrue(vm.session.value is SessionState.SignedOut)
    }
    @Test fun `login uses returned role and never trims a password`() = runTest(main.dispatcher) {
        runCurrent()
        login()
        vm.signIn()
        runCurrent()
        assertEquals(AuthFixtures.account, (vm.session.value as SessionState.Authenticated).account)
        assertEquals("  password123  ", repository.lastPassword)
        assertEquals("", vm.ui.value.login.password)
        assertEquals(AuthFixtures.session, sessions.read())
    }
    @Test fun `wrong password leaves the form available for correction`() = runTest(main.dispatcher) {
        runCurrent()
        repository.loginResult = AuthFixtures.failure(FailureKind.UNAUTHORIZED)
        login()
        vm.signIn()
        runCurrent()
        assertEquals(FailureKind.UNAUTHORIZED, vm.ui.value.login.failure!!.kind)
        assertEquals("  password123  ", vm.ui.value.login.password)
        assertFalse(vm.ui.value.login.submitting)
        assertTrue(vm.session.value is SessionState.SignedOut)
    }
    @Test fun `rapid double submit creates only one login request`() = runTest(main.dispatcher) {
        runCurrent()
        val response = CompletableDeferred<ApiResult<com.example.collabpro.features.identity.domain.model.Session>>()
        repository.loginCall = { response.await() }
        login()
        vm.signIn()
        vm.signIn()
        runCurrent()
        assertEquals(1, repository.loginCount)
        response.complete(ApiResult.Success(AuthFixtures.session))
        runCurrent()
    }
    @Test fun `leaving login cancels its request and clears sensitive form state`() = runTest(main.dispatcher) {
        runCurrent()
        val response = CompletableDeferred<ApiResult<com.example.collabpro.features.identity.domain.model.Session>>()
        repository.loginCall = { response.await() }
        login()
        vm.signIn()
        runCurrent()
        vm.enterForm(AuthForm.RECOVERY)
        response.complete(ApiResult.Success(AuthFixtures.session))
        runCurrent()
        assertEquals("", vm.ui.value.login.password)
        assertNull(sessions.read())
        assertTrue(vm.session.value is SessionState.SignedOut)
    }
    @Test fun `recovery displays server acknowledgement only after success`() = runTest(main.dispatcher) {
        runCurrent()
        vm.enterForm(AuthForm.RECOVERY)
        vm.recoveryEmail("test@example.test")
        vm.requestRecovery()
        assertNull(vm.ui.value.recovery.message)
        runCurrent()
        assertEquals(1, repository.recoveryCount)
        assertEquals((repository.recoveryResult as ApiResult.Success).value, vm.ui.value.recovery.message)
    }
    @Test fun `network failure in recovery is not presented as email sent`() = runTest(main.dispatcher) {
        runCurrent()
        repository.recoveryResult = AuthFixtures.failure(FailureKind.NETWORK)
        vm.enterForm(AuthForm.RECOVERY)
        vm.recoveryEmail("test@example.test")
        vm.requestRecovery()
        runCurrent()
        assertNull(vm.ui.value.recovery.message)
        assertEquals(FailureKind.NETWORK, vm.ui.value.recovery.failure!!.kind)
    }
    @Test fun `204 password reset clears old session and requires new login`() = runTest(main.dispatcher) {
        runCurrent()
        login()
        vm.signIn()
        runCurrent()
        vm.openPasswordReset("collabpro://password-reset?token=real-recovery-token")
        vm.newPassword("newpassword123")
        vm.passwordConfirmation("newpassword123")
        vm.resetPassword()
        runCurrent()
        assertEquals(1, repository.resetCount)
        assertEquals("real-recovery-token", repository.lastToken)
        assertTrue(vm.ui.value.reset.completed)
        assertEquals("", vm.ui.value.reset.password)
        assertEquals("", vm.ui.value.reset.confirmation)
        assertNull(sessions.read())
        assertTrue(vm.session.value is SessionState.SignedOut)
    }
    @Test fun `used or expired reset token never shows success`() = runTest(main.dispatcher) {
        runCurrent()
        repository.resetResult = AuthFixtures.failure(FailureKind.VALIDATION)
        vm.openPasswordReset("collabpro://password-reset?token=expired")
        vm.newPassword("newpassword123")
        vm.passwordConfirmation("newpassword123")
        vm.resetPassword()
        runCurrent()
        assertFalse(vm.ui.value.reset.completed)
        assertNotNull(vm.ui.value.reset.failure)
    }
    @Test fun `invalid links and mismatched passwords never call reset`() = runTest(main.dispatcher) {
        runCurrent()
        vm.openPasswordReset("collabpro://password-reset")
        vm.resetPassword()
        runCurrent()
        assertFalse(vm.ui.value.reset.linkValid)
        assertEquals(0, repository.resetCount)
        vm.openPasswordReset("collabpro://password-reset?token=valid-token")
        vm.newPassword("newpassword123")
        vm.passwordConfirmation("different123")
        vm.resetPassword()
        runCurrent()
        assertEquals(0, repository.resetCount)
        assertTrue(vm.ui.value.reset.failure!!.fieldErrors.containsKey("confirmation"))
    }
    @Test fun `authentication expiration runs without another network call`() = runTest(main.dispatcher) {
        runCurrent()
        login()
        vm.signIn()
        runCurrent()
        clock.time = AuthFixtures.session.expiresAt
        advanceTimeBy(3600000)
        runCurrent()
        assertTrue(vm.session.value is SessionState.SignedOut)
        assertNull(sessions.read())
    }
    @Test fun `opening recovery during cold restoration does not leave a stuck loading state`() = runTest(main.dispatcher) {
        vm.openPasswordReset("collabpro://password-reset?token=valid-token")
        runCurrent()
        vm.dismissPasswordReset()
        runCurrent()
        assertTrue(vm.session.value is SessionState.SignedOut)
        assertFalse(vm.ui.value.reset.isOpen)
    }
    @Test fun `diagnostic UI strings never expose passwords`() {
        assertFalse(LoginUiState(password = "private-value").toString().contains("private-value"))
        assertFalse(RegistrationUiState(password = "private-value").toString().contains("private-value"))
        assertFalse(ResetPasswordUiState(password = "private-value", confirmation = "private-value").toString().contains("private-value"))
    }
}
