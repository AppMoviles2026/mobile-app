package com.example.collabpro.campaign

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.di.NetworkModule
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.infrastructure.RemoteCampaignRepository
import com.example.collabpro.features.campaign.infrastructure.remote.CampaignApi
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.infrastructure.RemoteIdentityRepository
import com.example.collabpro.features.identity.infrastructure.remote.IdentityApi
import com.example.collabpro.features.identity.infrastructure.session.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.crypto.KeyGenerator

/** Opt-in: synthetic accounts/campaigns in an isolated loopback MySQL backend only. */
class RealCampaignDiscoveryIntegrationTest {
    @Test fun `published search filters pagination complete detail expiry closure and role protection with MySQL`() = runBlocking {
        val base = System.getenv("COLLABPRO_CAMPAIGN_TEST_API")
        assumeTrue("Isolated campaign integration is opt-in", !base.isNullOrBlank())
        val config = ApiConfiguration(base!!, true)
        require(config.url.host in listOf("127.0.0.1", "localhost"))
        val clock = Clock.systemUTC(); val gson = ApiJson.create()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val storage = object : EncryptedSessionStorage {
            var bytes: ByteArray? = null
            override fun read() = bytes
            override fun write(encrypted: ByteArray): Boolean { bytes = encrypted; return true }
            override fun clear(): Boolean { bytes = null; return true }
        }
        val sessions = EncryptedSessionStore(storage, SessionCipher { key }, gson, clock)
        val executor = ApiExecutor(gson, sessions, clock, config)
        val retrofit = Retrofit.Builder().baseUrl(config.url).client(NetworkModule.client(config, sessions, clock))
            .addConverterFactory(GsonConverterFactory.create(gson)).build()
        val identity = RemoteIdentityRepository(retrofit.create(IdentityApi::class.java), executor)
        val repository = RemoteCampaignRepository(retrofit.create(CampaignApi::class.java), executor)
        val authentication = AuthenticationSession(identity, sessions, clock)
        fun <T> ApiResult<T>.success(): T {
            assertTrue("Expected success, got $this", this is ApiResult.Success)
            return (this as ApiResult.Success).value
        }
        val token = "Discovery-${UUID.randomUUID()}"
        val brandEmail = "brand-$token@example.test"; val creatorEmail = "creator-$token@example.test"; val password = "Discovery-Only123!"
        identity.registerBrand("Empresa $token", brandEmail, password).success(); authentication.signIn(brandEmail, password).success()
        val category = "Moda $token"
        val body = NewCampaign("$token 50%_! A", "Objetivo de campaña", "Descripción completa", category, "Audiencia definida", "Jesús María, Lima")
        val deadline = clock.instant().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        val terms = CampaignConditions(listOf(
            NewRequirement("Confirmar plazos", true, RequirementRule.MANUAL_CONFIRMATION, null),
            NewRequirement("Nicho recomendado", false, RequirementRule.NICHE_EQUALS, "Moda"),
            NewRequirement("Ubicación recomendada", false, RequirementRule.LOCATION_EQUALS, "Lima"),
            NewRequirement("Red recomendada", false, RequirementRule.AUTHORIZED_PLATFORM, "instagram")),
            listOf(NewDeliverable("Video", "Mostrar el producto", 2, deadline.plus(1, ChronoUnit.DAYS))), deadline,
            Compensation(CompensationType.PRODUCT, null, null, "Producto por contenido"))
        suspend fun publish(input: NewCampaign, conditions: CampaignConditions): CampaignDetails {
            val id = repository.create(input, IdempotencyKey()).success().summary.id
            repository.saveConditions(id, conditions).success()
            return repository.publish(id).success()
        }
        val first = publish(body, terms)
        val second = publish(body.copy(title = "$token 50%_! B", location = "Cusco"), terms.copy(compensation = Compensation(CompensationType.CASH, BigDecimal("120.50"), "PEN", "Pago por contenido")))
        val draft = repository.create(body.copy(title = "$token sin publicar"), IdempotencyKey()).success()
        assertEquals(FailureKind.FORBIDDEN, (repository.search(CampaignSearch(query = token)) as ApiResult.Failure).error.kind)
        assertEquals(FailureKind.FORBIDDEN, (repository.published() as ApiResult.Failure).error.kind)
        authentication.signOut(); identity.registerCreator("Creador de prueba", creatorEmail, password).success(); authentication.signIn(creatorEmail, password).success()
        val p0 = repository.search(CampaignSearch(query = token), PageRequest(0, 1)).success()
        val p1 = repository.search(CampaignSearch(query = token), PageRequest(1, 1)).success()
        assertEquals(2L, p0.total); assertEquals(2L, p1.total)
        assertEquals(setOf(first.summary.id, second.summary.id), (p0.items + p1.items).map { it.id }.toSet())
        assertEquals(0, p0.page); assertEquals(1, p1.page); assertEquals(1, p1.size)
        val p2 = repository.search(CampaignSearch(query = token), PageRequest(2, 1)).success(); assertTrue(p2.items.isEmpty()); assertEquals(2L, p2.total)
        val combined = repository.search(CampaignSearch(query = token, category = category.uppercase(), location = "JESÚS", compensationType = CompensationType.PRODUCT)).success()
        assertEquals(first.summary.id, combined.items.single().id); assertEquals(1L, combined.total)
        assertEquals(2L, repository.search(CampaignSearch(query = "$token 50%_!")).success().total)
        assertEquals(0L, repository.search(CampaignSearch(query = "$token inexistente")).success().total)
        assertEquals(0L, repository.search(CampaignSearch(query = token, compensationType = CompensationType.SERVICE)).success().total)
        val public = repository.published(PageRequest(0, 100)).success()
        assertTrue(public.items.any { it.id == first.summary.id }); assertFalse(public.items.any { it.id == draft.summary.id })
        assertTrue(public.items.all { it.acceptsApplications && it.status == CampaignStatus.OPEN })
        val details = repository.details(first.summary.id).success()
        assertEquals(body.objective, details.objective); assertEquals(body.description, details.description); assertEquals(body.targetAudience, details.targetAudience)
        assertEquals(4, details.requirements.size); assertEquals(1, details.deliverables.size); assertEquals(2, details.deliverables.single().quantity)
        assertEquals(CompensationType.PRODUCT, details.summary.compensation!!.type); assertNotNull(details.publicationDate)
        assertEquals(CampaignAvailability.AVAILABLE, details.summary.availability(clock.instant()))
        assertEquals(FailureKind.FORBIDDEN, (repository.details(draft.summary.id) as ApiResult.Failure).error.kind)
        assertEquals(FailureKind.NOT_FOUND, (repository.details(UUID.randomUUID()) as ApiResult.Failure).error.kind)
        authentication.signOut(); authentication.signIn(brandEmail, password).success()
        repository.close(first.summary.id).success()
        val nearDeadline = clock.instant().plusSeconds(15).truncatedTo(ChronoUnit.SECONDS)
        val expiring = publish(body.copy(title = "$token expiración"), terms.copy(applicationDeadline = nearDeadline))
        authentication.signOut(); authentication.signIn(creatorEmail, password).success()
        val closed = repository.details(first.summary.id).success()
        assertEquals(CampaignStatus.CLOSED, closed.summary.status); assertFalse(closed.summary.acceptsApplications); assertEquals(4, closed.requirements.size)
        assertEquals(CampaignAvailability.CLOSED, closed.summary.availability(clock.instant()))
        assertFalse(repository.search(CampaignSearch(query = token)).success().items.any { it.id == first.summary.id })
        delay((Duration.between(clock.instant(), nearDeadline).toMillis() + 250).coerceAtLeast(1))
        val expired = repository.details(expiring.summary.id).success()
        assertEquals(CampaignStatus.OPEN, expired.summary.status); assertFalse(expired.summary.acceptsApplications)
        assertEquals(CampaignAvailability.EXPIRED, expired.summary.availability(clock.instant()))
        val after = repository.search(CampaignSearch(query = token)).success()
        assertEquals(1L, after.total); assertEquals(second.summary.id, after.items.single().id)
        assertFalse(repository.published(PageRequest(0, 100)).success().items.any { it.id == expiring.summary.id || it.id == first.summary.id })
        authentication.signOut()
        assertEquals(FailureKind.UNAUTHORIZED, (repository.published() as ApiResult.Failure).error.kind)
        Unit
    }
}
