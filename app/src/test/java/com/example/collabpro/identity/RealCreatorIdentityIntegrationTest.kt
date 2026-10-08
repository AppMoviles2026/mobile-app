package com.example.collabpro.identity

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.di.NetworkModule
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.application.social.SocialAuthorizationReturn
import com.example.collabpro.features.identity.application.usecases.IdentityUseCases
import com.example.collabpro.features.identity.domain.model.*
import com.example.collabpro.features.identity.infrastructure.RemoteIdentityRepository
import com.example.collabpro.features.identity.infrastructure.remote.IdentityApi
import com.example.collabpro.features.identity.infrastructure.session.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Clock
import java.util.UUID
import javax.crypto.KeyGenerator

/** Opt-in isolated backend; provider denial needs no third-party requests or real credentials. */
class RealCreatorIdentityIntegrationTest {
    @Test fun `real profile and Android OAuth denial callback enforce role owner state and no duplicate binding`() = runBlocking {
        val base = System.getenv("COLLABPRO_IDENTITY_TEST_API")
        assumeTrue("Isolated identity integration is opt-in", !base.isNullOrBlank())
        val configuration = ApiConfiguration(base!!, true)
        require(configuration.url.host in listOf("127.0.0.1", "localhost"))
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
        val api = Retrofit.Builder().baseUrl(configuration.url).client(NetworkModule.client(configuration, sessions, clock))
            .addConverterFactory(GsonConverterFactory.create(gson)).build().create(IdentityApi::class.java)
        val repository = RemoteIdentityRepository(api, executor)
        val authentication = AuthenticationSession(repository, sessions, clock)
        val useCases = IdentityUseCases(repository, sessions)
        val suffix = UUID.randomUUID()
        val password = "Identity-Test-Only123!"
        val email = "creator-$suffix@example.test"
        assertEquals(FailureKind.UNAUTHORIZED, (repository.creatorProfile() as ApiResult.Failure).error.kind)
        assertTrue(repository.registerCreator("Creador de integración", email, password) is ApiResult.Success)
        assertTrue(authentication.signIn(email, password) is ApiResult.Success)
        val initial = (repository.creatorProfile() as ApiResult.Success).value
        assertEquals(sessions.read()!!.account.profileId, initial.profileId)
        val update = CreatorProfileUpdate("Nombre actualizado", "Contenido local", "Gastronomía", "Adultos jóvenes", "Lima")
        val profile = (useCases.updateCreatorProfile(update) as ApiResult.Success).value
        assertEquals(initial.profileId, profile.profileId)
        assertEquals(update.displayName, profile.displayName); assertEquals(update.audienceDescription, profile.audienceDescription)
        assertEquals(profile, (repository.creatorProfile() as ApiResult.Success).value)
        assertTrue(authentication.refreshAccount() is ApiResult.Success)
        assertEquals(update.displayName, sessions.read()!!.account.name)
        assertEquals(FailureKind.VALIDATION, (repository.updateCreatorProfile(update.copy(displayName = "")) as ApiResult.Failure).error.kind)
        assertTrue((repository.socialAccounts() as ApiResult.Success).value.isEmpty())
        val publicBrowser = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
        val attempts = mutableListOf<UUID>()
        for (platform in SocialPlatform.entries) {
            val authorization = (useCases.startSocialAuthorization(platform) as ApiResult.Success).value
            attempts.add(authorization.authorizationId)
            assertTrue(SocialAuthorizationReturn.trustedBrowserUrl(authorization.authorizationUrl, platform))
            val pending = (useCases.getAuthorizationStatus(authorization.authorizationId) as ApiResult.Success).value
            assertEquals(platform, pending.platform); assertEquals(AuthorizationStatus.PENDING, pending.status)
            val state = authorization.authorizationUrl.toString().toHttpUrl().queryParameter("state")!!
            val callback = configuration.url.newBuilder().addPathSegments("social-accounts/${platform.wireValue}/callback")
                .addQueryParameter("state", state).addQueryParameter("error", "access_denied").build()
            publicBrowser.newCall(Request.Builder().url(callback).build()).execute().use { response ->
                assertEquals(303, response.code)
                assertEquals(authorization.authorizationId, SocialAuthorizationReturn.parse(response.header("Location")!!))
                assertEquals("no-store", response.header("Cache-Control"))
                assertFalse(response.header("Location")!!.contains("state="))
            }
            val denied = (useCases.getAuthorizationStatus(authorization.authorizationId) as ApiResult.Success).value
            assertEquals(AuthorizationStatus.FAILED, denied.status); assertEquals("AUTHORIZATION_DENIED", denied.errorCode)
            assertTrue((useCases.getSocialAccounts() as ApiResult.Success).value.isEmpty())
            publicBrowser.newCall(Request.Builder().url(callback).build()).execute().use { assertEquals(400, it.code) }
        }
        authentication.signOut()
        val other = "other-$suffix@example.test"
        assertTrue(repository.registerCreator("Otro creador", other, password) is ApiResult.Success)
        assertTrue(authentication.signIn(other, password) is ApiResult.Success)
        for (id in attempts) assertEquals("AUTHORIZATION_NOT_FOUND", (repository.authorizationStatus(id) as ApiResult.Failure).error.code)
        authentication.signOut()
        val brand = "brand-$suffix@example.test"
        assertTrue(repository.registerBrand("Empresa de prueba", brand, password) is ApiResult.Success)
        assertTrue(authentication.signIn(brand, password) is ApiResult.Success)
        assertEquals(FailureKind.FORBIDDEN, (repository.creatorProfile() as ApiResult.Failure).error.kind)
        assertEquals(FailureKind.FORBIDDEN, (repository.authorizeSocialAccount(SocialPlatform.INSTAGRAM) as ApiResult.Failure).error.kind)
        authentication.signOut()
        publicBrowser.connectionPool.evictAll()
        Unit
    }
}
