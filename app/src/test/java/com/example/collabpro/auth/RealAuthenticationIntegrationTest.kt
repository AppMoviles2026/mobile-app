package com.example.collabpro.auth

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.di.NetworkModule
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.identity.application.auth.*
import com.example.collabpro.features.identity.domain.model.AccountType
import com.example.collabpro.features.identity.infrastructure.RemoteIdentityRepository
import com.example.collabpro.features.identity.infrastructure.remote.IdentityApi
import com.example.collabpro.features.identity.infrastructure.session.*
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Clock
import java.util.UUID
import javax.crypto.KeyGenerator

/** Explicit opt-in: run ONLY against an isolated local backend and Mailpit, never real email recipients. */
class RealAuthenticationIntegrationTest {
    @Test fun `real backend registration roles recovery mail single use reset and JWT revocation`() = runBlocking {
        val baseUrl = System.getenv("COLLABPRO_AUTH_TEST_API")
        val mailpitUrl = System.getenv("COLLABPRO_AUTH_TEST_MAILPIT")
        assumeTrue("Local backend integration is opt-in", !baseUrl.isNullOrBlank() && !mailpitUrl.isNullOrBlank())
        val configuration = ApiConfiguration(baseUrl!!, true)
        require(configuration.url.host in listOf("localhost", "127.0.0.1"))
        require(java.net.URI(mailpitUrl!!).host in listOf("localhost", "127.0.0.1"))
        val clock = Clock.systemUTC()
        val gson = ApiJson.create()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val storage = object : EncryptedSessionStorage {
            var bytes: ByteArray? = null
            override fun read() = bytes
            override fun write(encrypted: ByteArray): Boolean { bytes = encrypted; return true }
            override fun clear(): Boolean { bytes = null; return true }
        }
        val sessions = EncryptedSessionStore(storage, SessionCipher { key }, gson, clock)
        val executor = ApiExecutor(gson, sessions, clock, configuration)
        val retrofit = Retrofit.Builder().baseUrl(configuration.url)
            .client(NetworkModule.client(configuration, sessions, clock))
            .addConverterFactory(GsonConverterFactory.create(gson)).build()
        val repository = RemoteIdentityRepository(retrofit.create(IdentityApi::class.java), executor)
        val controller = AuthenticationSession(repository, sessions, clock)
        val suffix = UUID.randomUUID().toString()
        val brandEmail = "brand-$suffix@example.test"
        val creatorEmail = "creator-$suffix@example.test"
        val oldPassword = "Only-Test-Pass123!"
        val newPassword = "New-Test-Pass456!"

        assertTrue(repository.registerBrand("Marca real de prueba", brandEmail, oldPassword) is ApiResult.Success)
        val duplicate = repository.registerBrand("Duplicada", brandEmail, oldPassword) as ApiResult.Failure
        assertEquals(FailureKind.CONFLICT, duplicate.error.kind)
        assertTrue(controller.signIn(brandEmail, oldPassword) is ApiResult.Success)
        assertEquals(AccountType.BRAND, (controller.state.value as SessionState.Authenticated).account.accountType)
        assertTrue(sessions.read()!!.accessToken.split('.').size == 3)
        controller.signOut()

        assertTrue(repository.registerCreator("Creador real de prueba", creatorEmail, oldPassword) is ApiResult.Success)
        assertTrue(controller.signIn(creatorEmail, oldPassword) is ApiResult.Success)
        assertEquals(AccountType.CREATOR, (controller.state.value as SessionState.Authenticated).account.accountType)
        val oldSession = sessions.read()!!
        val revokedToken = oldSession.accessToken
        assertEquals(FailureKind.UNAUTHORIZED, (repository.signIn(creatorEmail, "wrong-password") as ApiResult.Failure).error.kind)
        assertEquals(revokedToken, sessions.read()?.accessToken)
        controller.restore()
        assertTrue(controller.state.value is SessionState.Authenticated)

        val known = repository.requestRecovery(creatorEmail) as ApiResult.Success
        val unknown = repository.requestRecovery("unknown-$suffix@example.test") as ApiResult.Success
        assertEquals(known.value, unknown.value) // Account existence must not be exposed.
        val mailClient = OkHttpClient()
        fun readMail(path: String): JsonObject = mailClient.newCall(Request.Builder().url(mailpitUrl.trimEnd('/') + path).build())
            .execute().use { response ->
                require(response.isSuccessful)
                gson.fromJson(response.body!!.string(), JsonObject::class.java)
            }
        val link = withTimeout(30000) {
            var discovered: String? = null
            while (discovered == null) {
                val messages = readMail("/api/v1/messages").getAsJsonArray("messages")
                val summary = messages.firstOrNull { item ->
                    item.asJsonObject.getAsJsonArray("To").any { it.asJsonObject["Address"].asString == creatorEmail }
                }?.asJsonObject
                if (summary != null) {
                    val message = readMail("/api/v1/message/" + summary["ID"].asString)
                    discovered = Regex("collabpro://password-reset\\?token=[^\\s]+").find(message["Text"].asString)?.value
                }
                if (discovered == null) delay(250)
            }
            discovered
        }
        val parsed = PasswordResetLink.parse(link!!) as PasswordResetLink.Valid
        assertTrue(repository.resetPassword(parsed.token, newPassword) is ApiResult.Success)
        assertEquals("INVALID_RECOVERY_TOKEN", (repository.resetPassword(parsed.token, newPassword) as ApiResult.Failure).error.code)
        // Cold verification of the old JWT must fail even before its nominal expiration.
        controller.restore()
        assertTrue(controller.state.value is SessionState.SignedOut)
        assertNull(sessions.read())
        assertEquals(FailureKind.UNAUTHORIZED, (controller.signIn(creatorEmail, oldPassword) as ApiResult.Failure).error.kind)
        assertTrue(controller.signIn(creatorEmail, newPassword) is ApiResult.Success)
        assertNotEquals(revokedToken, sessions.read()?.accessToken)
        assertEquals(AccountType.CREATOR, (controller.state.value as SessionState.Authenticated).account.accountType)
        controller.signOut()
        Unit
    }
}
