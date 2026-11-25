package com.example.collabpro.identity

import androidx.lifecycle.ViewModelStore
import com.example.collabpro.auth.AuthFixtures
import com.example.collabpro.auth.FakeIdentity
import com.example.collabpro.auth.MainDispatcherRule
import com.example.collabpro.auth.MemorySessions
import com.example.collabpro.auth.MutableClock
import com.example.collabpro.core.domain.ApiFailure
import com.example.collabpro.core.domain.ApiResult
import com.example.collabpro.core.domain.FailureKind
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.application.auth.SessionState
import com.example.collabpro.features.identity.application.usecases.IdentityUseCases
import com.example.collabpro.features.identity.domain.model.AccountType
import com.example.collabpro.features.identity.domain.model.BusinessProfile
import com.example.collabpro.features.identity.domain.model.BusinessProfileUpdate
import com.example.collabpro.features.identity.domain.repositories.IdentityRepository
import com.example.collabpro.features.identity.presentation.profile.BrandProfileViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BrandProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val brand = AuthFixtures.account.copy(accountType = AccountType.BRAND, name = "Café")
    private val authRepository = FakeIdentity().apply { loginResult = ApiResult.Success(AuthFixtures.session.copy(account = brand)) }
    private val initial = BusinessProfile(brand.profileId, brand.name, null, null, null, 0)
    private val repository = ProfileRepository(authRepository, initial)
    private val sessions = MemorySessions()
    private val auth = AuthenticationSession(repository, sessions, MutableClock())
    private val owners = ViewModelStore()
    private lateinit var vm: BrandProfileViewModel

    @Before fun setup() {
        vm = BrandProfileViewModel(IdentityUseCases(repository, sessions), auth)
        owners.put("brand", vm)
    }

    @After fun cleanup() { owners.clear() }

    private suspend fun signIn() { auth.signIn("brand@example.test", "password123") }

    @Test fun `first edit saves the initial version and refreshes the verified session name`() = runTest(main.dispatcher) {
        signIn()
        vm.open()
        runCurrent()
        assertEquals("", vm.ui.value.draft?.description)
        vm.edit { copy(businessName = "  Café renovado ", description = "  Café de origen  ") }
        repository.write = {
            val saved = initial.copy(businessName = it.businessName, description = it.description, version = 1)
            authRepository.accountResult = ApiResult.Success(brand.copy(name = saved.businessName))
            ApiResult.Success(saved)
        }
        vm.save()
        vm.save()
        runCurrent()
        assertEquals(listOf(BusinessProfileUpdate("Café renovado", "Café de origen", null, null, 0)), repository.updates)
        assertEquals("Perfil guardado.", vm.ui.value.notice)
        assertEquals("Café renovado", (auth.state.value as SessionState.Authenticated).account.name)
        assertEquals(AuthFixtures.session.accessToken, sessions.read()?.accessToken)
    }

    @Test fun `conflict preserves edits and requires reviewing the new version before another PUT`() = runTest(main.dispatcher) {
        signIn()
        vm.open()
        runCurrent()
        vm.edit { copy(description = "Mi descripción") }
        repository.write = { ApiResult.Failure(ApiFailure(FailureKind.CONFLICT, "CONCURRENT_UPDATE", "Conflicto")) }
        vm.save()
        runCurrent()
        vm.save()
        assertEquals(1, repository.updates.size)
        repository.read = { ApiResult.Success(initial.copy(location = "Cusco", version = 1)) }
        vm.reload()
        runCurrent()
        assertEquals("Mi descripción", vm.ui.value.draft?.description)
        assertEquals("Cusco", vm.ui.value.latestForReview?.location)
        assertFalse(vm.ui.value.canSave)
        vm.review(keepEdits = true)
        assertEquals("Cusco", vm.ui.value.draft?.location)
        assertTrue(vm.ui.value.canSave)
        assertEquals(1, repository.updates.size)
        repository.write = { ApiResult.Success(initial.copy(description = it.description, location = it.location, version = 2)) }
        vm.save()
        runCurrent()
        assertEquals(BusinessProfileUpdate("Café", "Mi descripción", null, "Cusco", 1), repository.updates.last())
    }

    @Test fun `lost response is verified by reading the persisted profile without resending`() = runTest(main.dispatcher) {
        signIn()
        vm.open()
        runCurrent()
        vm.edit { copy(location = "Lima") }
        repository.write = { AuthFixtures.failure(FailureKind.TIMEOUT) }
        vm.save()
        runCurrent()
        assertTrue(vm.ui.value.needsReload)
        repository.read = { ApiResult.Success(initial.copy(location = "Lima", version = 1)) }
        vm.reload()
        runCurrent()
        assertEquals("Lima", vm.ui.value.profile?.location)
        assertFalse(vm.ui.value.dirty)
        assertFalse(vm.ui.value.needsReload)
        assertEquals(1, repository.updates.size)
    }

    @Test fun `leaving the account discards dirty form and late results and creator sends no profile request`() = runTest(main.dispatcher) {
        signIn()
        vm.open()
        runCurrent()
        vm.edit { copy(location = "Dato privado") }
        val late = CompletableDeferred<ApiResult<BusinessProfile>>()
        repository.write = { withContext(NonCancellable) { late.await() } }
        vm.save()
        runCurrent()
        auth.signOut()
        runCurrent()
        authRepository.loginResult = ApiResult.Success(AuthFixtures.session.copy(
            account = AuthFixtures.account.copy(accountId = UUID.randomUUID())
        ))
        signIn()
        vm.open()
        late.complete(ApiResult.Success(initial.copy(location = "Dato privado", version = 1)))
        runCurrent()
        assertNull(vm.ui.value.draft)
        assertNull(vm.ui.value.profile)
        assertEquals(1, repository.reads)
    }

    @Test fun `blank name is reported without losing edited optional fields or sending a PUT`() = runTest(main.dispatcher) {
        signIn()
        vm.open()
        runCurrent()
        vm.edit { copy(businessName = "  ", category = "Gastronomía") }
        vm.save()
        runCurrent()
        assertTrue(repository.updates.isEmpty())
        assertTrue(vm.ui.value.failure?.fieldErrors?.containsKey("businessName") == true)
        assertEquals("Gastronomía", vm.ui.value.draft?.category)
    }

    private class ProfileRepository(auth: FakeIdentity, initial: BusinessProfile) : IdentityRepository by auth {
        val updates = mutableListOf<BusinessProfileUpdate>()
        var reads = 0
        var read: suspend () -> ApiResult<BusinessProfile> = { ApiResult.Success(initial) }
        var write: suspend (BusinessProfileUpdate) -> ApiResult<BusinessProfile> = { error("Unexpected update") }

        override suspend fun brandProfile(): ApiResult<BusinessProfile> {
            reads++
            return read()
        }

        override suspend fun updateBrandProfile(update: BusinessProfileUpdate): ApiResult<BusinessProfile> {
            updates += update
            return write(update)
        }
    }
}
