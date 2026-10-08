package com.example.collabpro.foundation

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.di.NetworkModule
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.identity.infrastructure.RemoteIdentityRepository
import com.example.collabpro.features.identity.infrastructure.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException

class NetworkSecurityTest {
    private lateinit var server: MockWebServer
    private lateinit var configuration: ApiConfiguration
    private lateinit var executor: ApiExecutor
    private lateinit var repository: RemoteIdentityRepository
    private lateinit var sessions: FakeSessions

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        configuration = ApiConfiguration(server.url("/api/v1/").toString(), true)
        sessions = FakeSessions()
        executor = ApiExecutor(ApiJson.create(), sessions, ApiFixtures.clock, configuration)
        val retrofit = Retrofit.Builder().baseUrl(configuration.url)
            .client(NetworkModule.client(configuration, sessions, ApiFixtures.clock))
            .addConverterFactory(GsonConverterFactory.create(ApiJson.create())).build()
        repository = RemoteIdentityRepository(retrofit.create(IdentityApi::class.java), executor)
    }
    @After fun teardown() { server.shutdown() }

    private fun kind(result: ApiResult<*>) = (result as ApiResult.Failure).error.kind

    @Test fun `protected 401 invalidates the current session`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"INVALID_TOKEN","message":"Sesión inválida","fieldErrors":{}}"""))
        assertEquals(FailureKind.UNAUTHORIZED, kind(repository.currentAccount()))
        assertNull(sessions.current)
        assertEquals(1, sessions.invalidations)
    }
    @Test fun `login 401 does not invalidate a different existing session`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"INVALID_CREDENTIALS","message":"Datos inválidos"}"""))
        assertEquals(FailureKind.UNAUTHORIZED, kind(repository.signIn("user@example.com", "wrong-password")))
        assertNotNull(sessions.current)
        assertEquals(0, sessions.invalidations)
        assertNull(server.takeRequest().getHeader("Authorization"))
    }
    @Test fun `expired sessions are rejected before any request`() = runTest {
        sessions.current = sessions.current!!.copy(expiresAt = ApiFixtures.now)
        assertEquals(FailureKind.UNAUTHORIZED, kind(repository.currentAccount()))
        assertEquals(0, server.requestCount)
        assertNull(sessions.current)
    }
    @Test fun `missing sessions never send a protected request`() = runTest {
        sessions.current = null
        assertEquals(FailureKind.UNAUTHORIZED, kind(repository.currentAccount()))
        assertEquals(0, server.requestCount)
    }
    @Test fun `late 401 cannot sign out a newer account`() = runTest {
        val result = executor.protectedCall<AccountDto, Unit>({
            sessions.switchAccount()
            Response.error(401, "{}".toResponseBody())
        }) { Unit }
        assertEquals(FailureKind.SESSION_CHANGED, kind(result))
        assertEquals("another.jwt.token", sessions.current?.accessToken)
        assertEquals(0, sessions.invalidations)
    }
    @Test fun `successful resources of a previous session are discarded`() = runTest {
        var mapped = false
        val result = executor.protectedCall<String, String>({ sessions.switchAccount(); Response.success("old account data") }) {
            mapped = true; it
        }
        assertEquals(FailureKind.SESSION_CHANGED, kind(result))
        assertFalse(mapped)
    }
    @Test fun `connection failures preserve valid sessions`() = runTest {
        val result = executor.protectedCall<String, String>({ throw IOException("Do not expose this raw failure") }) { it }
        assertEquals(FailureKind.NETWORK, kind(result))
        assertNotNull(sessions.current)
        assertFalse(result.toString().contains("raw failure"))
    }
    @Test fun `timeout is distinct from a failed business operation`() = runTest {
        val result = executor.protectedCall<String, String>({ throw SocketTimeoutException() }) { it }
        assertEquals(FailureKind.TIMEOUT, kind(result))
        assertNotNull(sessions.current)
    }
    @Test fun `coroutine cancellation is propagated instead of becoming an API error`() = runTest {
        try {
            executor.publicCall<String, String>({ throw CancellationException("cancelled") }) { it }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { /* expected */ }
    }
    @Test fun `structured errors retain their code and field errors`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"VALIDATION_ERROR","message":"Revisa los campos","fieldErrors":{"displayName":"Obligatorio"}}"""))
        val failure = (repository.creatorProfile() as ApiResult.Failure).error
        assertEquals(FailureKind.VALIDATION, failure.kind)
        assertEquals("VALIDATION_ERROR", failure.code)
        assertEquals("Obligatorio", failure.fieldErrors["displayName"])
        assertEquals(400, failure.httpStatus)
    }
    @Test fun `permission conflict missing requirement and provider failures remain different`() = runTest {
        val cases = mapOf(403 to FailureKind.FORBIDDEN, 404 to FailureKind.NOT_FOUND, 409 to FailureKind.CONFLICT,
            422 to FailureKind.REQUIREMENTS_NOT_MET, 502 to FailureKind.PROVIDER_UNAVAILABLE,
            503 to FailureKind.PROVIDER_UNAVAILABLE, 500 to FailureKind.SERVER)
        for ((status, expected) in cases) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("<html>Not JSON</html>"))
            assertEquals(expected, kind(repository.currentAccount()))
            assertNotNull(sessions.current)
        }
    }
    @Test fun `empty success body is not a fabricated account`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(FailureKind.MALFORMED_RESPONSE, kind(repository.currentAccount()))
    }
    @Test fun `missing required fields are not filled from previews`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        assertEquals(FailureKind.MALFORMED_RESPONSE, kind(repository.currentAccount()))
    }
    @Test fun `unknown enums are rejected`() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.account.replace("BRAND", "ADMIN")))
        assertEquals(FailureKind.MALFORMED_RESPONSE, kind(repository.currentAccount()))
    }
    @Test fun `non canonical UUIDs are rejected`() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.account.replace(ApiFixtures.ID, "1-1-1-1-1")))
        assertEquals(FailureKind.MALFORMED_RESPONSE, kind(repository.currentAccount()))
    }
    @Test fun `invalid dates and token types are rejected`() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.session.replace("2030-01-01T01:00:00Z", "tomorrow")))
        assertEquals(FailureKind.MALFORMED_RESPONSE, kind(repository.signIn("a@b.com", "password")))
        server.enqueue(MockResponse().setBody(ApiFixtures.session.replace("Bearer", "Basic")))
        assertEquals(FailureKind.MALFORMED_RESPONSE, kind(repository.signIn("a@b.com", "password")))
    }
    @Test fun `null collection elements do not crash or become an empty list`() = runTest {
        server.enqueue(MockResponse().setBody("[null]"))
        assertEquals(FailureKind.MALFORMED_RESPONSE, kind(repository.socialAccounts()))
    }
    @Test fun `public callback never receives bearer even if a caller sets it`() {
        server.enqueue(MockResponse().setBody("{}"))
        val client = NetworkModule.client(configuration, sessions, ApiFixtures.clock)
        client.newCall(Request.Builder().url(server.url("/api/v1/social-accounts/instagram/callback?state=opaque"))
            .header("Authorization", "Bearer should-not-leak").build()).execute().use { assertEquals(200, it.code) }
        assertNull(server.takeRequest().getHeader("Authorization"))
    }
    @Test fun `session snapshot is checked again immediately before sending`() {
        val client = NetworkModule.client(configuration, sessions, ApiFixtures.clock)
        val oldCredentials = sessions.current!!
        sessions.switchAccount()
        try {
            client.newCall(Request.Builder().url(server.url("/api/v1/accounts/me"))
                .tag(com.example.collabpro.core.application.security.SessionCredentials::class.java, oldCredentials).build()).execute().close()
            fail("Old session must not be sent")
        } catch (_: IOException) { /* expected */ }
        assertEquals(0, server.requestCount)
    }
    @Test fun `redirects are not followed to external providers`() = runTest {
        val other = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/capture")))
            assertEquals(FailureKind.SERVER, kind(repository.currentAccount()))
            assertEquals(0, other.requestCount)
        } finally { other.shutdown() }
    }
    @Test fun `untrusted hosts paths and ports never receive requests`() {
        val client = NetworkModule.client(configuration, sessions, ApiFixtures.clock)
        val other = MockWebServer().apply { start() }
        try {
            for (url in listOf(other.url("/api/v1/accounts/me"), server.url("/api/v10/accounts/me"))) {
                try { client.newCall(Request.Builder().url(url).build()).execute().close(); fail("Must reject untrusted URL") }
                catch (_: IOException) { /* expected */ }
            }
            assertEquals(0, server.requestCount)
            assertEquals(0, other.requestCount)
        } finally { other.shutdown() }
    }
    @Test fun `release refuses cleartext API configuration`() {
        try { ApiConfiguration("http://example.com/api/v1/", false); fail("HTTPS required") }
        catch (_: IllegalArgumentException) { /* expected */ }
    }
    @Test fun `unconfigured production URL reports configuration failure`() = runTest {
        val unconfigured = ApiExecutor(ApiJson.create(), sessions, ApiFixtures.clock, ApiConfiguration("https://unconfigured.invalid/api/v1/", false))
        var called = false
        val result = unconfigured.publicCall<String, String>({ called = true; Response.success("bad") }) { it }
        assertEquals(FailureKind.CONFIGURATION, kind(result))
        assertFalse(called)
    }
}
