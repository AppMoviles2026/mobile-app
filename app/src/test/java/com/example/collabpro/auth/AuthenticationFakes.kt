package com.example.collabpro.auth

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.identity.domain.model.*
import com.example.collabpro.features.identity.domain.repositories.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.*
import java.util.UUID

internal object AuthFixtures {
    val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
    val account = Account(UUID.randomUUID(), UUID.randomUUID(), "Nombre real", AccountType.CREATOR, AccountStatus.ACTIVE)
    val session = Session(account, "test.jwt.token", now.plusSeconds(3600))
    fun failure(kind: FailureKind) = ApiResult.Failure(ApiFailure(kind, message = "Error real de prueba"))
}
internal class MutableClock(var time: Instant = AuthFixtures.now) : Clock() {
    override fun instant() = time
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}
internal class MemorySessions(var stored: Session? = null) : SessionStore {
    override val changes = MutableStateFlow(0L)
    var saveFailure = false
    var clears = 0
    override suspend fun read() = stored
    override suspend fun save(session: Session): ApiResult<Unit> {
        if (saveFailure) return AuthFixtures.failure(FailureKind.STORAGE)
        stored = session
        changes.value++
        return ApiResult.Success(Unit)
    }
    override suspend fun clear(): ApiResult<Unit> {
        stored = null
        clears++
        changes.value++
        return ApiResult.Success(Unit)
    }
}
internal class FakeIdentity : IdentityRepository {
    var loginResult: ApiResult<Session> = ApiResult.Success(AuthFixtures.session)
    var accountResult: ApiResult<Account> = ApiResult.Success(AuthFixtures.account)
    var registrationResult: ApiResult<Account> = ApiResult.Success(AuthFixtures.account)
    var recoveryResult: ApiResult<String> = ApiResult.Success("Si existe la cuenta, enviaremos un correo.")
    var resetResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var loginCall: suspend () -> ApiResult<Session> = { loginResult }
    var accountCall: suspend () -> ApiResult<Account> = { accountResult }
    var registerCall: suspend () -> ApiResult<Account> = { registrationResult }
    var loginCount = 0
    var accountCount = 0
    var brandCount = 0
    var creatorCount = 0
    var recoveryCount = 0
    var resetCount = 0
    var lastEmail = ""
    var lastPassword = ""
    var lastName = ""
    var lastToken = ""
    override suspend fun signIn(email: String, password: String): ApiResult<Session> {
        loginCount++; lastEmail = email; lastPassword = password; return loginCall()
    }
    override suspend fun currentAccount(): ApiResult<Account> { accountCount++; return accountCall() }
    override suspend fun registerBrand(businessName: String, email: String, password: String): ApiResult<Account> {
        brandCount++; lastName = businessName; lastEmail = email; lastPassword = password; return registerCall()
    }
    override suspend fun registerCreator(displayName: String, email: String, password: String): ApiResult<Account> {
        creatorCount++; lastName = displayName; lastEmail = email; lastPassword = password; return registerCall()
    }
    override suspend fun requestRecovery(email: String): ApiResult<String> { recoveryCount++; lastEmail = email; return recoveryResult }
    override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> {
        resetCount++; lastToken = token; lastPassword = newPassword; return resetResult
    }
    override suspend fun creatorProfile(): ApiResult<CreatorProfile> = error("Unexpected profile request")
    override suspend fun updateCreatorProfile(update: CreatorProfileUpdate): ApiResult<CreatorProfile> = error("Unexpected profile request")
    override suspend fun authorizeSocialAccount(platform: SocialPlatform): ApiResult<SocialAuthorization> = error("Unexpected OAuth request")
    override suspend fun socialAccounts(): ApiResult<List<SocialAccount>> = error("Unexpected OAuth request")
    override suspend fun authorizationStatus(id: UUID): ApiResult<AuthorizationAttempt> = error("Unexpected OAuth request")
}
