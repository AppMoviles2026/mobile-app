package com.example.collabpro.campaign

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.di.NetworkModule
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.campaign.application.drafts.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.campaign.domain.repositories.CampaignRepository
import com.example.collabpro.features.campaign.infrastructure.RemoteCampaignRepository
import com.example.collabpro.features.campaign.infrastructure.remote.CampaignApi
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
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

/** Explicit opt-in isolated MySQL backend. Lost responses are injected after actual committed HTTP operations. */
class RealCampaignPreparationIntegrationTest {
    @Test fun `real drafts conditions publication lost responses visibility ownership discard and closure`() = runBlocking {
        val base = System.getenv("COLLABPRO_CAMPAIGN_TEST_API")
        assumeTrue("Isolated campaign integration is opt-in", !base.isNullOrBlank())
        val configuration = ApiConfiguration(base!!, true)
        require(configuration.url.host in listOf("localhost", "127.0.0.1"))
        val gson = ApiJson.create(); val clock = Clock.systemUTC()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val storage = object : EncryptedSessionStorage {
            var bytes: ByteArray? = null
            override fun read() = bytes
            override fun write(encrypted: ByteArray): Boolean { bytes = encrypted; return true }
            override fun clear(): Boolean { bytes = null; return true }
        }
        val sessions = EncryptedSessionStore(storage, SessionCipher { key }, gson, clock)
        val executor = ApiExecutor(gson, sessions, clock, configuration)
        val retrofit = Retrofit.Builder().baseUrl(configuration.url).client(NetworkModule.client(configuration, sessions, clock))
            .addConverterFactory(GsonConverterFactory.create(gson)).build()
        val identity = RemoteIdentityRepository(retrofit.create(IdentityApi::class.java), executor)
        val repository = RemoteCampaignRepository(retrofit.create(CampaignApi::class.java), executor)
        val authentication = AuthenticationSession(identity, sessions, clock)
        val suffix = UUID.randomUUID()
        val email = "campaign-brand-$suffix@example.test"; val password = "Campaign-Test-Only123!"
        assertTrue(identity.registerBrand("Empresa de campañas", email, password) is ApiResult.Success)
        assertTrue(authentication.signIn(email, password) is ApiResult.Success)
        val brandId = sessions.read()!!.account.profileId
        val input = NewCampaign("Campaña $suffix", "Presentar producto", "Descripción real", "Moda", "Adultos jóvenes", "Lima")
        val requestKey = IdempotencyKey()
        val draft = (repository.create(input, requestKey) as ApiResult.Success).value
        assertEquals(CampaignStatus.DRAFT, draft.summary.status)
        assertEquals(draft.summary.id, (repository.create(input, requestKey) as ApiResult.Success).value.summary.id)
        assertEquals("IDEMPOTENCY_KEY_REUSED", (repository.create(input.copy(title = "Distinta"), requestKey) as ApiResult.Failure).error.code)
        assertEquals("INCOMPLETE_CAMPAIGN", (repository.publish(draft.summary.id) as ApiResult.Failure).error.code)
        val applicationDeadline = clock.instant().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES)
        val conditions = CampaignConditions(listOf(NewRequirement("Confirmar plazos", true, RequirementRule.MANUAL_CONFIRMATION, null),
            NewRequirement("Nicho", false, RequirementRule.NICHE_EQUALS, "Moda")),
            listOf(NewDeliverable("Video", "Mostrar producto", 2, applicationDeadline.plus(1, ChronoUnit.DAYS))), applicationDeadline,
            Compensation(CompensationType.PRODUCT, null, null, "Producto por contenido"))
        val invalid = conditions.copy(deliverables = listOf(conditions.deliverables.single().copy(deadline = applicationDeadline)))
        assertEquals("INVALID_CONDITIONS", (repository.saveConditions(draft.summary.id, invalid) as ApiResult.Failure).error.code)
        var saved = draft.toDraft("UTC").copy(conditions = ConditionsDraft(
            requirements = conditions.requirements.map { RequirementDraft(description = it.description, mandatory = it.mandatory, rule = it.ruleType, expectedValue = it.expectedValue.orEmpty()) },
            deliverables = conditions.deliverables.map { DeliverableDraft(contentType = it.contentType, description = it.description, quantity = it.quantity.toString(), deadline = CampaignDates.display(it.deadline, "UTC")) },
            applicationDeadline = CampaignDates.display(applicationDeadline, "UTC"), compensationType = CompensationType.PRODUCT,
            compensationDescription = conditions.compensation.description, zoneId = "UTC"))
        var putCount = 0; var publishCount = 0
        var losePut = true; var losePublication = true
        val unreliable = object : CampaignRepository by repository {
            override suspend fun saveConditions(id: UUID, body: CampaignConditions): ApiResult<CampaignDetails> {
                putCount++; val committed = repository.saveConditions(id, body)
                return if (committed is ApiResult.Success && losePut) { losePut = false; CampaignFixtures.failure(FailureKind.NETWORK) } else committed
            }
            override suspend fun publish(id: UUID): ApiResult<CampaignDetails> {
                publishCount++; val committed = repository.publish(id)
                return if (committed is ApiResult.Success && losePublication) { losePublication = false; CampaignFixtures.failure(FailureKind.TIMEOUT) } else committed
            }
        }
        val workflow = CampaignPreparation(unreliable, clock)
        suspend fun prepare() = workflow.execute(brandId, saved, PreparationGoal.PUBLICATION) { next, _ -> saved = next; ApiResult.Success(Unit) }
        assertTrue(prepare() is ApiResult.Failure); assertEquals(DraftOperation.CONDITIONS, saved.pending)
        assertTrue((repository.details(draft.summary.id) as ApiResult.Success).value.matches(conditions))
        assertTrue(prepare() is ApiResult.Failure); assertEquals(DraftOperation.PUBLISH, saved.pending)
        assertEquals(CampaignStatus.OPEN, (repository.details(draft.summary.id) as ApiResult.Success).value.summary.status)
        assertTrue(prepare() is ApiResult.Success); assertEquals(1, putCount); assertEquals(1, publishCount)
        assertEquals(draft.summary.id, saved.serverId); assertNull(saved.pending)
        assertEquals("CAMPAIGN_NOT_DRAFT", (repository.discardDraft(saved.serverId!!) as ApiResult.Failure).error.code)
        val mine = (repository.mine() as ApiResult.Success).value
        assertEquals(1, mine.items.count { it.id == draft.summary.id })
        val disposable = (repository.create(input.copy(title = "Borrador descartable $suffix"), IdempotencyKey()) as ApiResult.Success).value
        assertTrue(repository.discardDraft(disposable.summary.id) is ApiResult.Success)
        assertEquals(FailureKind.NOT_FOUND, (repository.details(disposable.summary.id) as ApiResult.Failure).error.kind)
        authentication.signOut()
        val creator = "campaign-creator-$suffix@example.test"
        assertTrue(identity.registerCreator("Creador de campañas", creator, password) is ApiResult.Success)
        assertTrue(authentication.signIn(creator, password) is ApiResult.Success)
        assertTrue((repository.published() as ApiResult.Success).value.items.any { it.id == draft.summary.id })
        assertEquals(CampaignStatus.OPEN, (repository.details(draft.summary.id) as ApiResult.Success).value.summary.status)
        assertEquals(FailureKind.FORBIDDEN, (repository.create(input, IdempotencyKey()) as ApiResult.Failure).error.kind)
        authentication.signOut()
        val other = "campaign-other-$suffix@example.test"
        assertTrue(identity.registerBrand("Otra empresa", other, password) is ApiResult.Success)
        assertTrue(authentication.signIn(other, password) is ApiResult.Success)
        assertEquals(FailureKind.FORBIDDEN, (repository.saveConditions(draft.summary.id, conditions) as ApiResult.Failure).error.kind)
        authentication.signOut(); assertTrue(authentication.signIn(email, password) is ApiResult.Success)
        assertEquals(CampaignStatus.CLOSED, (repository.close(draft.summary.id) as ApiResult.Success).value.summary.status)
        assertEquals(CampaignStatus.CLOSED, (repository.close(draft.summary.id) as ApiResult.Success).value.summary.status)
        assertNotNull((repository.details(draft.summary.id) as ApiResult.Success).value)
        authentication.signOut()
        Unit
    }
}
