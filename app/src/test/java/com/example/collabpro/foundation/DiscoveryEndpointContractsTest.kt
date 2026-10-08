package com.example.collabpro.foundation

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.di.NetworkModule
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.infrastructure.RemoteCampaignRepository
import com.example.collabpro.features.campaign.infrastructure.remote.CampaignApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class DiscoveryEndpointContractsTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteCampaignRepository
    private val sessions = FakeSessions()
    @Before fun setup() {
        server = MockWebServer().apply { start() }
        val config = ApiConfiguration(server.url("/api/v1/").toString(), true); val gson = ApiJson.create()
        val retrofit = Retrofit.Builder().baseUrl(config.url).client(NetworkModule.client(config, sessions, ApiFixtures.clock))
            .addConverterFactory(GsonConverterFactory.create(gson)).build()
        repository = RemoteCampaignRepository(retrofit.create(CampaignApi::class.java), ApiExecutor(gson, sessions, ApiFixtures.clock, config))
    }
    @After fun cleanup() { server.shutdown() }
    private fun reply(body: String, code: Int = 200) { server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)) }
    private fun request() = server.takeRequest(2, TimeUnit.SECONDS)!!
    @Test fun `all filters including accents reserved characters and page reach server unchanged`() = runTest {
        reply(ApiFixtures.page(ApiFixtures.campaign).replace("\"page\":0", "\"page\":2").replace("\"size\":20", "\"size\":7"))
        val filters = CampaignSearch("Café 50%_! & +", "Gastronomía", "Jesús María", CompensationType.BARTER)
        assertTrue(repository.search(filters, PageRequest(2, 7)) is ApiResult.Success)
        val request = request(); val url = request.requestUrl!!
        assertEquals("/api/v1/campaigns", url.encodedPath); assertEquals(filters.query, url.queryParameter("q"))
        assertEquals(filters.category, url.queryParameter("category")); assertEquals(filters.location, url.queryParameter("location"))
        assertEquals("BARTER", url.queryParameter("compensationType")); assertEquals("2", url.queryParameter("page")); assertEquals("7", url.queryParameter("size"))
        assertEquals("Bearer test.jwt.token", request.getHeader("Authorization")); assertEquals("GET", request.method)
    }
    @Test fun `absent search filters are omitted rather than sent as null strings`() = runTest {
        reply(ApiFixtures.page(ApiFixtures.campaign)); assertTrue(repository.search(CampaignSearch()) is ApiResult.Success)
        assertEquals(setOf("page", "size"), request().requestUrl!!.queryParameterNames)
    }
    @Test fun `published page total and requested pagination are parsed without local filtering`() = runTest {
        reply("""{"items":[${ApiFixtures.campaign}],"total":41,"page":1,"size":20}""")
        val value = (repository.published(PageRequest(1, 20)) as ApiResult.Success).value
        assertEquals(41L, value.total); assertEquals(1, value.page); assertEquals(20, value.size)
        assertEquals("/api/v1/campaigns/published?page=1&size=20", request().path)
    }
    @Test fun `closed campaign retains all conditions with negative server availability`() = runTest {
        reply(ApiFixtures.campaign.replace("\"status\":\"OPEN\"", "\"status\":\"CLOSED\"").replace("\"acceptsApplications\":true", "\"acceptsApplications\":false"))
        val detail = (repository.details(ApiFixtures.id) as ApiResult.Success).value
        assertEquals(CampaignStatus.CLOSED, detail.summary.status); assertFalse(detail.summary.acceptsApplications)
        assertEquals("Promoción", detail.objective); assertEquals(1, detail.requirements.size); assertEquals(1, detail.deliverables.size)
        assertEquals("9999999999.99", detail.summary.compensation!!.amount!!.toPlainString()); request()
    }
    @Test fun `missing availability and malformed pagination are failures not empty lists`() = runTest {
        reply(ApiFixtures.page(ApiFixtures.campaign.replace(",\"acceptsApplications\":true", "")))
        assertEquals(FailureKind.MALFORMED_RESPONSE, (repository.published() as ApiResult.Failure).error.kind); request()
        reply("""{"items":[],"total":-1,"page":0,"size":20}""")
        assertEquals(FailureKind.MALFORMED_RESPONSE, (repository.search(CampaignSearch()) as ApiResult.Failure).error.kind); request()
    }
    @Test fun `detail 404 does not fabricate data and 401 invalidates actual session`() = runTest {
        reply("""{"code":"CAMPAIGN_NOT_FOUND","message":"No encontrada"}""", 404)
        assertEquals(FailureKind.NOT_FOUND, (repository.details(ApiFixtures.id) as ApiResult.Failure).error.kind); request()
        reply("""{"code":"UNAUTHORIZED"}""", 401)
        assertEquals(FailureKind.UNAUTHORIZED, (repository.published() as ApiResult.Failure).error.kind); request()
        assertNull(sessions.current); assertEquals(1, sessions.invalidations)
    }
}
