package com.example.collabpro.foundation

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.di.NetworkModule
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.identity.domain.model.*
import com.example.collabpro.features.identity.infrastructure.RemoteIdentityRepository
import com.example.collabpro.features.identity.infrastructure.remote.IdentityApi
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.infrastructure.*
import com.example.collabpro.features.campaign.infrastructure.remote.*
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit

class EndpointContractsTest {
    private lateinit var server: MockWebServer
    private lateinit var identity: RemoteIdentityRepository
    private lateinit var campaigns: RemoteCampaignRepository
    private lateinit var applications: RemoteApplicationRepository
    private val sessions = FakeSessions()
    private val id = ApiFixtures.id

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        val configuration = ApiConfiguration(server.url("/api/v1/").toString(), true)
        val gson = ApiJson.create()
        val executor = ApiExecutor(gson, sessions, ApiFixtures.clock, configuration)
        val retrofit = Retrofit.Builder().baseUrl(configuration.url)
            .client(NetworkModule.client(configuration, sessions, ApiFixtures.clock))
            .addConverterFactory(GsonConverterFactory.create(gson)).build()
        identity = RemoteIdentityRepository(retrofit.create(IdentityApi::class.java), executor)
        campaigns = RemoteCampaignRepository(retrofit.create(CampaignApi::class.java), executor)
        applications = RemoteApplicationRepository(retrofit.create(ApplicationApi::class.java), executor)
    }
    @After fun teardown() { server.shutdown() }

    private suspend fun <T> verify(method: String, path: String, response: String?, protected: Boolean = true,
        code: Int = 200, bodyKeys: Set<String>? = null, key: IdempotencyKey? = null, call: suspend () -> ApiResult<T>): T {
        val reply = MockResponse().setResponseCode(code)
        if (response != null) reply.setHeader("Content-Type", "application/json").setBody(response)
        server.enqueue(reply)
        val result = call()
        assertTrue("$method $path failed: $result", result is ApiResult.Success)
        val request = server.takeRequest(2, TimeUnit.SECONDS) ?: error("Missing request")
        assertEquals(method, request.method)
        assertEquals("/api/v1/$path", request.path)
        assertEquals(if (protected) "Bearer test.jwt.token" else null, request.getHeader("Authorization"))
        if (bodyKeys != null) assertEquals(bodyKeys, JsonParser.parseString(request.body.readUtf8()).asJsonObject.keySet())
        assertEquals(key?.value?.toString(), request.getHeader("Idempotency-Key"))
        return (result as ApiResult.Success).value
    }

    @Test fun `all eleven identity operations match the backend contract`() = runTest {
        verify("POST", "auth/brands", ApiFixtures.account, false, 201, setOf("businessName", "email", "password")) {
            identity.registerBrand("Marca", "brand@example.com", "password123")
        }
        verify("POST", "auth/creators", ApiFixtures.account, false, 201, setOf("displayName", "email", "password")) {
            identity.registerCreator("Ana", "ana@example.com", "password123")
        }
        val session = verify("POST", "auth/sessions", ApiFixtures.session, false, bodyKeys = setOf("email", "password")) {
            identity.signIn("ana@example.com", "password123")
        }
        assertEquals(ApiFixtures.now.plusSeconds(3600), session.expiresAt)
        verify("POST", "auth/recovery-requests", """{"message":"Correo enviado"}""", false, 202, setOf("email")) {
            identity.requestRecovery("ana@example.com")
        }
        verify("POST", "auth/password-resets", null, false, 204, setOf("token", "newPassword")) {
            identity.resetPassword("opaque-reset-token", "password123")
        }
        verify("GET", "accounts/me", ApiFixtures.account) { identity.currentAccount() }
        val profile = verify("GET", "profiles/me/creator", ApiFixtures.profile) { identity.creatorProfile() }
        assertNull(profile.niche)
        verify("PUT", "profiles/me/creator", ApiFixtures.profile, bodyKeys = setOf("displayName")) {
            identity.updateCreatorProfile(CreatorProfileUpdate("Ana"))
        }
        verify("POST", "social-accounts/instagram/authorizations?client=ANDROID", ApiFixtures.authorization) {
            identity.authorizeSocialAccount(SocialPlatform.INSTAGRAM)
        }
        val accounts = verify("GET", "social-accounts/me", "[${ApiFixtures.social}]") { identity.socialAccounts() }
        assertEquals(SocialPlatform.INSTAGRAM, accounts.single().platform)
        verify("GET", "social-accounts/authorizations/$id", ApiFixtures.attempt) { identity.authorizationStatus(id) }
        assertEquals(11, server.requestCount)
    }

    @Test fun `all fourteen campaign operations match the backend contract`() = runTest {
        val key = IdempotencyKey()
        val draft = verify("POST", "campaigns", ApiFixtures.draft, code = 201,
            bodyKeys = setOf("title", "objective", "category", "targetAudience"), key = key) {
            campaigns.create(NewCampaign("Título", "Objetivo", null, "Belleza", "Jóvenes", null), key)
        }
        assertNull(draft.summary.compensation)
        val conditions = CampaignConditions(
            listOf(NewRequirement("Confirmación", true, RequirementRule.MANUAL_CONFIRMATION, null)),
            listOf(NewDeliverable("Reel", "Contenido", 1, ApiFixtures.now.plusSeconds(9000))),
            ApiFixtures.now.plusSeconds(5000), Compensation(CompensationType.CASH, BigDecimal("9999999999.99"), "PEN", "Pago"))
        verify("PUT", "campaigns/$id/conditions", ApiFixtures.campaign,
            bodyKeys = setOf("requirements", "deliverables", "applicationDeadline", "compensation")) { campaigns.saveConditions(id, conditions) }
        val published = verify("POST", "campaigns/$id/publication", ApiFixtures.campaign) { campaigns.publish(id) }
        assertEquals(BigDecimal("9999999999.99"), published.summary.compensation?.amount)
        assertTrue(published.summary.acceptsApplications)
        verify("GET", "campaigns/mine?page=0&size=20", ApiFixtures.page(ApiFixtures.draft)) { campaigns.mine(PageRequest()) }
        verify("GET", "campaigns/published?page=0&size=20", ApiFixtures.page(ApiFixtures.campaign)) { campaigns.published(PageRequest()) }
        verify("GET", "campaigns?q=reel&category=Belleza&location=Lima&compensationType=CASH&page=0&size=20", ApiFixtures.page(ApiFixtures.campaign)) {
            campaigns.search(CampaignSearch("reel", "Belleza", "Lima", CompensationType.CASH), PageRequest())
        }
        verify("GET", "campaigns/$id", ApiFixtures.campaign) { campaigns.details(id) }
        verify("DELETE", "campaigns/$id", null, code = 204) { campaigns.discardDraft(id) }
        verify("POST", "campaigns/$id/closure", ApiFixtures.campaign.replace("\"OPEN\"", "\"CLOSED\"").replace("\"acceptsApplications\":true", "\"acceptsApplications\":false")) {
            campaigns.close(id)
        }
        verify("POST", "campaigns/$id/applications", ApiFixtures.application, code = 201,
            bodyKeys = setOf("message", "confirmedRequirementIds"), key = key) {
            applications.submit(id, "Mi propuesta", setOf(UUID.fromString(ApiFixtures.REQUIREMENT_ID)), key)
        }
        verify("GET", "applications/mine?page=0&size=20", ApiFixtures.page(ApiFixtures.application)) { applications.mine(PageRequest()) }
        verify("GET", "applications/$id", ApiFixtures.application) { applications.details(id) }
        verify("PUT", "applications/$id", ApiFixtures.application, bodyKeys = setOf("message", "expectedVersion")) {
            applications.updateMessage(id, "Propuesta corregida", 0)
        }
        verify("POST", "applications/$id/cancellation", ApiFixtures.application.replace("PENDING", "CANCELLED"), bodyKeys = setOf("expectedVersion")) {
            applications.cancel(id, 0)
        }
        assertEquals(14, server.requestCount)
    }

    @Test fun `conditions serialize amounts exactly and never send child IDs or actor IDs`() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.campaign))
        val conditions = CampaignConditions(listOf(NewRequirement("Requisito", false, RequirementRule.NICHE_EQUALS, "Belleza")),
            listOf(NewDeliverable("Formato libre", "Contenido", 2, ApiFixtures.now.plusSeconds(9000))),
            ApiFixtures.now.plusSeconds(5000), Compensation(CompensationType.CASH, BigDecimal("9999999999.99"), "PEN", "Pago"))
        assertTrue(campaigns.saveConditions(id, conditions) is ApiResult.Success)
        val body = server.takeRequest().body.readUtf8()
        val json = JsonParser.parseString(body).asJsonObject
        assertTrue(body.contains("9999999999.99"))
        assertEquals(setOf("description", "mandatory", "ruleType", "expectedValue"), json.getAsJsonArray("requirements")[0].asJsonObject.keySet())
        assertEquals(setOf("contentType", "description", "quantity", "deadline"), json.getAsJsonArray("deliverables")[0].asJsonObject.keySet())
        assertFalse(body.contains("brandId"))
        assertFalse(body.contains("creatorId"))
        assertTrue(body.contains("2030-01-01T02:30:00Z"))
    }

    @Test fun `one idempotency key survives retries of the same creation intent`() = runTest {
        val key = IdempotencyKey()
        val input = NewCampaign("Título", "Objetivo", null, "Belleza", "Jóvenes", null)
        repeat(2) {
            verify<CampaignDetails>("POST", "campaigns", ApiFixtures.draft, key = key) { campaigns.create(input, key) }
        }
    }

    @Test fun `password whitespace is not changed by infrastructure`() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.session))
        identity.signIn("ana@example.com", "  secret123  ")
        val json = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("  secret123  ", json["password"].asString)
    }
}
