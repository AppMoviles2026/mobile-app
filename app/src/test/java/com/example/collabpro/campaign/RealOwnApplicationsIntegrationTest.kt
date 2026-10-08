package com.example.collabpro.campaign

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.di.NetworkModule
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.infrastructure.*
import com.example.collabpro.features.campaign.infrastructure.remote.*
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.domain.model.CreatorProfileUpdate
import com.example.collabpro.features.identity.infrastructure.RemoteIdentityRepository
import com.example.collabpro.features.identity.infrastructure.remote.IdentityApi
import com.example.collabpro.features.identity.infrastructure.session.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.crypto.KeyGenerator

/** Opt-in: five real endpoints, JWT and MySQL, using synthetic isolated loopback data. */
class RealOwnApplicationsIntegrationTest {
    @Test fun `own submission requirements idempotency ownership edit concurrency cancellation and history with MySQL`() = runBlocking {
        val base = System.getenv("COLLABPRO_CAMPAIGN_TEST_API")
        assumeTrue("Isolated integration is opt-in", !base.isNullOrBlank())
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
        val campaigns = RemoteCampaignRepository(retrofit.create(CampaignApi::class.java), executor)
        val applications = RemoteApplicationRepository(retrofit.create(ApplicationApi::class.java), executor)
        val authentication = AuthenticationSession(identity, sessions, clock)
        fun <T> ApiResult<T>.success(): T {
            assertTrue("Expected success, got $this", this is ApiResult.Success)
            return (this as ApiResult.Success).value
        }
        fun <T> ApiResult<T>.code(expected: String) {
            assertTrue("Expected $expected, got $this", this is ApiResult.Failure)
            assertEquals(expected, (this as ApiResult.Failure).error.code)
        }
        val token = UUID.randomUUID().toString()
        val brandEmail = "brand-app-$token@example.test"; val creatorEmail = "creator-app-$token@example.test"
        val otherEmail = "other-app-$token@example.test"; val password = "Applications-Only123!"
        identity.registerBrand("Empresa de prueba", brandEmail, password).success()
        authentication.signIn(brandEmail, password).success()
        val campaignId = campaigns.create(NewCampaign("Campaña $token", "Colaborar", "Descripción", "Moda", "Adultos", "Lima"), IdempotencyKey()).success().summary.id
        val deadline = clock.instant().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        val campaign = campaigns.saveConditions(campaignId, CampaignConditions(
            listOf(NewRequirement("Confirmar plazo", true, RequirementRule.MANUAL_CONFIRMATION, null),
                NewRequirement("Nicho Moda", true, RequirementRule.NICHE_EQUALS, "Moda"),
                NewRequirement("Confirmar experiencia", false, RequirementRule.MANUAL_CONFIRMATION, null)),
            listOf(NewDeliverable("Video", "Mostrar producto", 1, deadline.plus(1, ChronoUnit.DAYS))), deadline,
            Compensation(CompensationType.PRODUCT, null, null, "Producto por contenido"))).success()
        campaigns.publish(campaignId).success()
        assertEquals(FailureKind.FORBIDDEN, (applications.mine() as ApiResult.Failure).error.kind)
        authentication.signOut()
        identity.registerCreator("Creador de prueba", creatorEmail, password).success()
        authentication.signIn(creatorEmail, password).success()
        val profile = identity.updateCreatorProfile(CreatorProfileUpdate("Creador de prueba", niche = "Tecnología", location = "Lima")).success()
        val manual = campaign.requirements.single { it.ruleType == RequirementRule.MANUAL_CONFIRMATION && it.mandatory }.id
        val automatic = campaign.requirements.single { it.ruleType == RequirementRule.NICHE_EQUALS }.id
        applications.submit(campaignId, "Propuesta", setOf(UUID.randomUUID()), IdempotencyKey()).code("INVALID_CONFIRMATION")
        applications.submit(campaignId, "Propuesta", setOf(automatic), IdempotencyKey()).code("INVALID_CONFIRMATION")
        applications.submit(campaignId, "Propuesta", emptySet(), IdempotencyKey()).code("REQUIREMENTS_NOT_MET")
        val unmet = applications.submit(campaignId, "Propuesta", setOf(manual), IdempotencyKey()) as ApiResult.Failure
        assertEquals("REQUIREMENTS_NOT_MET", unmet.error.code)
        assertTrue(unmet.error.fieldErrors.containsKey("requirements.$automatic"))
        identity.updateCreatorProfile(CreatorProfileUpdate("Creador de prueba", niche = "Moda", location = "Lima")).success()
        val submissionKey = IdempotencyKey()
        val original = applications.submit(campaignId, "Propuesta original", setOf(manual), submissionKey).success()
        assertEquals(profile.profileId, original.creatorId); assertEquals(ApplicationStatus.PENDING, original.status)
        assertEquals(0L, original.version); assertEquals(setOf(manual), original.confirmedRequirementIds)
        assertEquals(original, applications.submit(campaignId, "Propuesta original", setOf(manual), submissionKey).success())
        applications.submit(campaignId, "Otro mensaje", setOf(manual), submissionKey).code("IDEMPOTENCY_KEY_REUSED")
        applications.submit(campaignId, "Otro mensaje", setOf(manual), IdempotencyKey()).code("APPLICATION_ALREADY_EXISTS")
        val page = applications.mine(PageRequest(0, 1)).success()
        assertEquals(1L, page.total); assertEquals(original.id, page.items.single().id)
        assertTrue(applications.mine(PageRequest(1, 1)).success().items.isEmpty())
        assertEquals(original, applications.details(original.id).success())
        authentication.signOut(); identity.registerCreator("Otro creador", otherEmail, password).success()
        authentication.signIn(otherEmail, password).success()
        assertEquals(FailureKind.NOT_FOUND, (applications.details(original.id) as ApiResult.Failure).error.kind)
        assertEquals(FailureKind.FORBIDDEN, (applications.updateMessage(original.id, "No autorizado", original.version) as ApiResult.Failure).error.kind)
        assertEquals(FailureKind.FORBIDDEN, (applications.cancel(original.id, original.version) as ApiResult.Failure).error.kind)
        authentication.signOut(); authentication.signIn(brandEmail, password).success()
        assertEquals(FailureKind.FORBIDDEN, (applications.details(original.id) as ApiResult.Failure).error.kind)
        campaigns.close(campaignId).success()
        authentication.signOut(); authentication.signIn(creatorEmail, password).success()
        // A closed campaign still permits editing/cancelling its existing pending application.
        val edited = applications.updateMessage(original.id, "Propuesta editada", original.version).success()
        assertEquals(1L, edited.version); assertEquals(original.confirmedRequirementIds, edited.confirmedRequirementIds)
        assertEquals(original.submittedAt, edited.submittedAt)
        applications.updateMessage(original.id, "Cambio obsoleto", original.version).code("CONCURRENT_UPDATE")
        applications.cancel(original.id, original.version).code("CONCURRENT_UPDATE")
        assertEquals(edited, applications.details(original.id).success())
        val cancelled = applications.cancel(original.id, edited.version).success()
        assertEquals(ApplicationStatus.CANCELLED, cancelled.status); assertEquals(2L, cancelled.version)
        assertEquals(edited.message, cancelled.message); assertEquals(original.confirmedRequirementIds, cancelled.confirmedRequirementIds)
        assertEquals(cancelled, applications.mine().success().items.single())
        applications.cancel(original.id, cancelled.version).code("APPLICATION_NOT_PENDING")
        applications.updateMessage(original.id, "Cambio terminal", cancelled.version).code("APPLICATION_NOT_PENDING")
        applications.submit(campaignId, "Volver a postular", setOf(manual), IdempotencyKey()).code("APPLICATION_ALREADY_EXISTS")
        // Idempotent POST can replay an old snapshot; only GET reports the current terminal status.
        assertEquals(ApplicationStatus.PENDING, applications.submit(campaignId, "Propuesta original", setOf(manual), submissionKey).success().status)
        assertEquals(cancelled, applications.details(original.id).success())
        authentication.signOut()
        assertEquals(FailureKind.UNAUTHORIZED, (applications.mine() as ApiResult.Failure).error.kind)
        Unit
    }
}
