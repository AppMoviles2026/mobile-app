package com.example.collabpro.foundation

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.di.NetworkModule
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.infrastructure.RemoteApplicationRepository
import com.example.collabpro.features.campaign.infrastructure.remote.ApplicationApi
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

class ApplicationEndpointContractsTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteApplicationRepository
    private val sessions = FakeSessions()
    @Before fun setup() {
        server = MockWebServer().apply { start() }; val config = ApiConfiguration(server.url("/api/v1/").toString(), true)
        val gson = ApiJson.create(); val retrofit = Retrofit.Builder().baseUrl(config.url).client(NetworkModule.client(config, sessions, ApiFixtures.clock))
            .addConverterFactory(GsonConverterFactory.create(gson)).build()
        repository = RemoteApplicationRepository(retrofit.create(ApplicationApi::class.java), ApiExecutor(gson, sessions, ApiFixtures.clock, config))
    }
    @After fun cleanup() { server.shutdown() }
    private fun reply(body: String = ApiFixtures.application, code: Int = 200) { server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)) }
    private fun request() = server.takeRequest(2, TimeUnit.SECONDS)!!
    @Test fun `submit uses stable idempotency key and exact UUID confirmations`() = runTest {
        val key = IdempotencyKey(); val confirmation = UUID.fromString(ApiFixtures.REQUIREMENT_ID)
        repeat(2) {
            reply(code = 201); assertTrue(repository.submit(ApiFixtures.id, "Propuesta", setOf(confirmation), key) is ApiResult.Success)
            val request = request(); assertEquals("/api/v1/campaigns/${ApiFixtures.ID}/applications", request.path)
            assertEquals("Bearer test.jwt.token", request.getHeader("Authorization")); assertEquals(key.value.toString(), request.getHeader("Idempotency-Key"))
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(setOf("message", "confirmedRequirementIds"), body.keySet()); assertEquals(confirmation.toString(), body["confirmedRequirementIds"].asJsonArray.single().asString)
        }
    }
    @Test fun `update sends consulted expectedVersion with message only and no confirmation rewrite`() = runTest {
        reply(); repository.updateMessage(ApiFixtures.id, "Mensaje cambiado", 12)
        val request = request(); assertEquals("PUT", request.method)
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("message", "expectedVersion"), body.keySet()); assertEquals(12L, body["expectedVersion"].asLong)
        assertNull(request.getHeader("Idempotency-Key"))
    }
    @Test fun `cancel sends mandatory expectedVersion and preserves response record`() = runTest {
        reply(ApiFixtures.application.replace("PENDING", "CANCELLED").replace("\"version\":0", "\"version\":8"))
        val value = (repository.cancel(ApiFixtures.id, 7) as ApiResult.Success).value
        assertEquals(ApplicationStatus.CANCELLED, value.status); assertEquals(8L, value.version)
        val request = request(); assertEquals("/api/v1/applications/${ApiFixtures.ID}/cancellation", request.path)
        assertEquals("POST", request.method); val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("expectedVersion"), body.keySet()); assertEquals(7L, body["expectedVersion"].asLong)
    }
    @Test fun `concurrent update and unmet requirement fields retain business codes and UUIDs`() = runTest {
        reply("""{"code":"CONCURRENT_UPDATE","message":"Cambió la versión","fieldErrors":{}}""", 409)
        val conflict = (repository.updateMessage(ApiFixtures.id, "Cambio", 0) as ApiResult.Failure).error
        assertEquals(FailureKind.CONFLICT, conflict.kind); assertEquals("CONCURRENT_UPDATE", conflict.code); request()
        reply("""{"code":"REQUIREMENTS_NOT_MET","message":"No cumples","fieldErrors":{"requirements.${ApiFixtures.REQUIREMENT_ID}":"Nicho Moda"}}""", 422)
        val requirements = (repository.submit(ApiFixtures.id, "Propuesta", emptySet(), IdempotencyKey()) as ApiResult.Failure).error
        assertEquals(FailureKind.REQUIREMENTS_NOT_MET, requirements.kind); assertEquals("Nicho Moda", requirements.fieldErrors["requirements.${ApiFixtures.REQUIREMENT_ID}"]); request()
    }
    @Test fun `missing or negative version is malformed instead of defaulting to zero`() = runTest {
        for (body in listOf(ApiFixtures.application.replace(",\"version\":0", ""), ApiFixtures.application.replace("\"version\":0", "\"version\":-1"))) {
            reply(body); assertEquals(FailureKind.MALFORMED_RESPONSE, (repository.details(ApiFixtures.id) as ApiResult.Failure).error.kind); request()
        }
    }
    @Test fun `POST and GET timestamps use the same persisted microsecond precision`() = runTest {
        for ((post, get) in listOf("2030-01-01T00:00:00.123456200Z" to "2030-01-01T00:00:00.123456Z",
            "2030-01-01T00:00:00.123456700Z" to "2030-01-01T00:00:00.123457Z",
            "2030-01-01T00:00:00.999999500Z" to "2030-01-01T00:00:01Z")) {
            reply(ApiFixtures.application.replace("2030-01-01T00:00:00Z", post), 201)
            val created = (repository.submit(ApiFixtures.id, "Mi propuesta", emptySet(), IdempotencyKey()) as ApiResult.Success).value
            request(); reply(ApiFixtures.application.replace("2030-01-01T00:00:00Z", get))
            val current = (repository.details(ApiFixtures.id) as ApiResult.Success).value
            request(); assertEquals(created, current)
        }
    }
}
