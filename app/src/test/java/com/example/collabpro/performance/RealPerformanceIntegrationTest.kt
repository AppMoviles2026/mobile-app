package com.example.collabpro.performance

import com.example.collabpro.core.application.security.SessionAccess
import com.example.collabpro.core.application.security.SessionCredentials
import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.di.NetworkModule
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.collaboration.domain.model.CollaborationRecord
import com.example.collabpro.features.collaboration.infrastructure.remote.CollaborationApi
import com.example.collabpro.features.identity.infrastructure.RemoteIdentityRepository
import com.example.collabpro.features.identity.infrastructure.remote.IdentityApi
import com.example.collabpro.features.performance.application.PerformanceInputs
import com.example.collabpro.features.performance.domain.model.*
import com.example.collabpro.features.performance.infrastructure.remote.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID

class RealPerformanceIntegrationTest {
    @Test fun `seeded private report evidence review and attribution work against the real backend`() = runBlocking {
        val url = System.getenv("COLLABPRO_PERFORMANCE_TEST_API")
        val password = System.getenv("COLLABPRO_PERFORMANCE_TEST_PASSWORD")
        assumeTrue("An isolated seeded backend is opt-in", !url.isNullOrBlank() && !password.isNullOrBlank())
        val config = ApiConfiguration(url!!, true)
        require(config.url.host in listOf("localhost", "127.0.0.1"))
        val brand = connect(config, "demo.brand@collabpro.app", password!!)
        val creator = connect(config, "demo.creator2@collabpro.app", password)
        val outsider = connect(config, "demo.creator@collabpro.app", password)
        val collaboration = creator.collaborations().single()
        val id = collaboration.id

        val metrics = creator.performance.metrics(id, PageRequest()).success()
        assertEquals("NO_DATA", metrics.automaticAvailability)
        assertTrue(creator.performance.snapshots(id, PageRequest()).success().items.isEmpty())
        assertTrue(outsider.performance.metrics(id, PageRequest()) is ApiResult.Failure)
        val seed = brand.performance.attribution(id, PageRequest(), null).success()
        assertTrue(seed.demonstrationTotals.codeRedemptions >= 1)
        assertTrue(seed.warning.isNotBlank())

        val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jHVkAAAAASUVORK5CYII=")
        val file = Document("stats.png", "image/png", png)
        val upload = UUID.randomUUID()
        val asset = creator.performance.upload(id, upload, file).success()
        assertEquals(asset.id, creator.performance.upload(id, upload, file).success().id)
        assertTrue(brand.performance.asset(asset.id) is ApiResult.Failure)

        val time = Instant.now().minusSeconds(1)
        val body = RegisterEvidence(UUID.randomUUID(), null, "Prueba Android con evidencia privada",
            listOf(Reference(assetId = asset.id)), mapOf("VIEWS" to 125), time, Period(time.minusSeconds(60), time))
        val evidence = creator.performance.register(id, body).success()
        assertEquals(evidence.id, creator.performance.register(id, body).success().id)
        assertEquals("MANUAL", brand.performance.evidenceDetail(evidence.id).success().sourceType)
        assertTrue(brand.performance.evidence(id, PageRequest(), "UNVERIFIED").success().items.any { it.id == evidence.id })
        val sharedAsset = brand.performance.asset(asset.id).success()
        assertArrayEquals(png, brand.performance.download(sharedAsset).success())
        assertTrue(outsider.performance.asset(asset.id) is ApiResult.Failure)

        val unrelated = body.copy(requestId = UUID.randomUUID(), description = "Referencia secundaria de prueba",
            references = listOf(Reference(url = "https://example.com/stats")))
        val reviewed = creator.performance.register(id, unrelated).success()
        val verification = ReviewEvidence(UUID.randomUUID(), reviewed.version, EvidenceDecision.VERIFIED, "Referencia consultada")
        assertTrue(creator.performance.review(reviewed.id, verification) is ApiResult.Failure)
        assertEquals("VERIFIED", brand.performance.review(reviewed.id, verification).success().status)

        val code = brand.performance.create(id, CreateMechanism(UUID.randomUUID(), MechanismKind.CODE, "Código Android", null)).success()
        assertNull(code.trackingUrl)
        assertNotNull(code.code)
        val interaction = RecordInteraction(UUID.randomUUID(), UUID.randomUUID(), InteractionType.CODE_REDEMPTION, code.createdAt, evidence.id)
        val recorded = brand.performance.record(code.id, interaction).success()
        assertEquals(recorded.id, brand.performance.record(code.id, interaction.copy(requestId = UUID.randomUUID())).success().id)
        assertEquals(1L, creator.performance.interactions(code.id, PageRequest(), null).success().total)
        assertEquals("BRAND_DECLARATION", recorded.source)

        val rejection = ReviewEvidence(UUID.randomUUID(), evidence.version, EvidenceDecision.REJECTED, "Dato de prueba descartado")
        assertEquals("REJECTED", brand.performance.review(evidence.id, rejection).success().status)
        val audited = creator.performance.interactions(code.id, PageRequest(), null).success().items.single()
        assertFalse(audited.includedInTotals)
        assertEquals("REJECTED", audited.evidenceStatus)
        assertEquals(MechanismStatus.INACTIVE, brand.performance.change(code.id,
            ChangeMechanism(UUID.randomUUID(), code.version, MechanismStatus.INACTIVE)).success().status)

        val destination = brand.performance.destinations().success().first { it.id == "demo" }
        val link = brand.performance.create(id, CreateMechanism(UUID.randomUUID(), MechanismKind.LINK, "Enlace Android", destination.id)).success()
        val shared = creator.performance.attribution(id, PageRequest(), null).success().mechanisms.items.first { it.mechanism.id == link.id }
        val tracking = requireNotNull(PerformanceInputs.tracking(shared.mechanism.trackingUrl))
        assertEquals(config.url.host, tracking.host)
        assertEquals(config.url.port, tracking.port)
        val browser = OkHttpClient.Builder().followRedirects(false).build()
        val head = Request.Builder().url(tracking.toString()).head().build()
        browser.newCall(head).execute().use { assertEquals(302, it.code); assertEquals(destination.url, it.header("Location")) }
        assertEquals(0L, creator.performance.interactions(link.id, PageRequest(), null).success().total)
        val visit = Request.Builder().url(tracking.toString()).header("Idempotency-Key", UUID.randomUUID().toString()).build()
        repeat(2) { browser.newCall(visit).execute().use { assertEquals(302, it.code) } }
        val clicks = creator.performance.interactions(link.id, PageRequest(), null).success()
        assertEquals(1L, clicks.total)
        assertEquals("FIRST_PARTY_TRACKING", clicks.items.single().source)
        assertNull(clicks.items.single().evidenceId)
        Unit
    }

    private suspend fun connect(config: ApiConfiguration, email: String, password: String): Client {
        val gson = ApiJson.create()
        val clock = Clock.systemUTC()
        val session = TestSession()
        val executor = ApiExecutor(gson, session, clock, config)
        val retrofit = Retrofit.Builder().baseUrl(config.url).client(NetworkModule.client(config, session, clock))
            .addConverterFactory(GsonConverterFactory.create(gson)).build()
        val identity = RemoteIdentityRepository(retrofit.create(IdentityApi::class.java), executor)
        val actor = identity.signIn(email, password).success()
        session.current = SessionCredentials(actor.account.accountId, actor.accessToken, actor.expiresAt, 1)
        return Client(RemotePerformance(retrofit.create(PerformanceApi::class.java), executor),
            retrofit.create(CollaborationApi::class.java), session.current!!)
    }

    private data class Client(val performance: RemotePerformance, val api: CollaborationApi, val actor: SessionCredentials) {
        suspend fun collaborations(): List<CollaborationRecord> = requireNotNull(api.collaborations(actor, null, 0, 20).body()?.items)
    }
    private class TestSession : SessionAccess {
        var current: SessionCredentials? = null
        override fun credentials() = current
        override fun isCurrent(credentials: SessionCredentials) = current == credentials
        override fun invalidateIfCurrent(credentials: SessionCredentials) { if (isCurrent(credentials)) current = null }
    }

    private fun <T> ApiResult<T>.success(): T = when (this) {
        is ApiResult.Success -> value
        is ApiResult.Failure -> error("Expected success, received ${error.kind} / ${error.code}: ${error.message}")
    }
}
