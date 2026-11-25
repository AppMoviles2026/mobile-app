package com.example.collabpro.campaign

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.di.NetworkModule
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.campaign.domain.model.*
import com.example.collabpro.features.collaboration.domain.model.*
import com.example.collabpro.features.collaboration.infrastructure.remote.CollaborationApi
import com.example.collabpro.features.collaboration.infrastructure.remote.RemoteCollaborations
import com.example.collabpro.features.campaign.application.dashboard.LoadOwnActivityTotal
import com.example.collabpro.features.campaign.infrastructure.*
import com.example.collabpro.features.campaign.infrastructure.remote.*
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.application.auth.SessionState
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
        val evaluation = RemoteApplicantEvaluation(retrofit.create(ApplicantEvaluationApi::class.java), executor)
        val collaborations = RemoteCollaborations(retrofit.create(CollaborationApi::class.java), executor)
        val totals = LoadOwnActivityTotal(campaigns, applications)
        val authentication = AuthenticationSession(identity, sessions, clock)
        fun <T> ApiResult<T>.code(expected: String) {
            assertTrue("Expected $expected, got $this", this is ApiResult.Failure)
            assertEquals(expected, (this as ApiResult.Failure).error.code)
        }
        val token = UUID.randomUUID().toString()
        val brandEmail = "brand-app-$token@example.test"; val creatorEmail = "creator-app-$token@example.test"
        val otherEmail = "other-app-$token@example.test"; val password = "Applications-Only123!"
        identity.registerBrand("Empresa de prueba", brandEmail, password).success()
        authentication.signIn(brandEmail, password).success()
        val brand = (authentication.state.value as SessionState.Authenticated).account
        assertEquals(0L, totals.campaigns(brand.profileId).success())
        val metadata = NewCampaign("Campaña $token", "Colaborar", "Descripción", "Moda", "Adultos", "Lima")
        val initial = campaigns.create(metadata, IdempotencyKey()).success()
        val campaignId = initial.summary.id
        val patched = campaigns.updateDraft(campaignId, metadata.copy(description = null), initial.version!!).success()
        assertNull(patched.description)
        campaigns.updateDraft(campaignId, metadata, initial.version).code("CONCURRENT_UPDATE")
        val deadline = clock.instant().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        val campaign = campaigns.saveConditions(campaignId, CampaignConditions(
            listOf(NewRequirement("Confirmar plazo", true, RequirementRule.MANUAL_CONFIRMATION, null),
                NewRequirement("Nicho Moda", true, RequirementRule.NICHE_EQUALS, "Moda"),
                NewRequirement("Confirmar experiencia", false, RequirementRule.MANUAL_CONFIRMATION, null)),
            listOf(NewDeliverable("Video", "Mostrar producto", 1, deadline.plus(1, ChronoUnit.DAYS))), deadline,
            Compensation(CompensationType.PRODUCT, null, null, "Producto por contenido"), expectedVersion = patched.version)).success()
        assertTrue(campaign.version!! > patched.version!!) 
        campaigns.publish(campaignId).success()
        assertEquals(1L, totals.campaigns(brand.profileId).success())
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
        val firstPage = evaluation.list(campaignId, PageRequest(0, 1), ApplicationStatus.PENDING).success()
        assertEquals(original.id, firstPage.items.single().application.id)
        assertEquals(profile.profileId, evaluation.detail(campaignId, original.id).success().creator?.id)
        authentication.signOut(); authentication.signIn(otherEmail, password).success()
        assertTrue(evaluation.list(campaignId, PageRequest(), null) is ApiResult.Failure)
        identity.updateCreatorProfile(CreatorProfileUpdate("Otro creador", niche = "Moda")).success()
        val another = applications.submit(campaignId, "Otra propuesta", setOf(manual), IdempotencyKey()).success()
        authentication.signOut()
        val thirdEmail = "selected-app-$token@example.test"
        identity.registerCreator("Creador seleccionado", thirdEmail, password).success()
        authentication.signIn(thirdEmail, password).success()
        identity.updateCreatorProfile(CreatorProfileUpdate("Creador seleccionado", niche = "Moda")).success()
        val third = applications.submit(campaignId, "Tercera propuesta", setOf(manual), IdempotencyKey()).success()
        authentication.signOut(); authentication.signIn(brandEmail, password).success()
        campaigns.close(campaignId).success()
        evaluation.decide(campaignId, another.id, ApplicantDecision.REJECTED, "Otra propuesta encaja mejor", another.version).success()
        val selected = evaluation.decide(campaignId, third.id, ApplicantDecision.SELECTED, null, third.version).success()
        assertEquals(third.id, selected.application.id)
        assertEquals(ApplicationStatus.SELECTED, selected.application.status)
        assertEquals(1L, evaluation.list(campaignId, PageRequest(), ApplicationStatus.PENDING).success().total)
        collaborationJourney(collaborations, authentication, clock, brandEmail, thirdEmail, password, third.id)
        authentication.signOut(); authentication.signIn(otherEmail, password).success()
        val rejected = applications.details(another.id).success()
        assertEquals("Otra propuesta encaja mejor", rejected.decisionReason)
        assertNotNull(rejected.decidedAt)
        assertEquals(ApplicationStatus.REJECTED, rejected.status)
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
        assertEquals(1L, totals.applications(profile.profileId).success())
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

    private suspend fun collaborationJourney(
        collaborations: RemoteCollaborations, authentication: AuthenticationSession, clock: Clock,
        brandEmail: String, thirdEmail: String, password: String, selectedApplication: UUID
    ) {
        val agreement = collaborations.proposeAgreement(ProposeAgreement(selectedApplication)).success()
        assertEquals(agreement.id, collaborations.proposeAgreement(ProposeAgreement(selectedApplication)).success().id)
        assertEquals(selectedApplication, agreement.snapshot.applicationId)
        assertEquals(agreement.id, collaborations.agreements("PENDING", PageRequest()).success().items.single().id)
        val acceptance = DecideAgreement(agreement.snapshot.termsVersion, AgreementDecision.ACCEPT)
        assertNull(collaborations.decideAgreement(agreement.id, acceptance).success().collaborationId)

        authentication.signOut(); authentication.signIn(thirdEmail, password).success()
        val signed = collaborations.decideAgreement(agreement.id, acceptance).success()
        val collaborationId = checkNotNull(signed.collaborationId)
        assertEquals(collaborationId, collaborations.recoverCollaboration(RecoverCollaboration(agreement.id)).success().id)
        val root = collaborations.collaboration(collaborationId).success()
        assertEquals(collaborationId, collaborations.collaborations(CollaborationStatus.ACTIVE, PageRequest()).success().items.single().id)
        val obligationId = root.deliverables.single().deliverableId
        val raw = "%PDF-1.7\nEvidence test\n%%EOF".encodeToByteArray()
        val uploadId = UUID.randomUUID()
        val doc = UploadDocument("evidence.pdf", "application/pdf", raw)
        val asset = collaborations.upload(collaborationId, uploadId, doc, false).success()
        assertEquals(asset.id, collaborations.upload(collaborationId, uploadId, doc, false).success().id)
        val content = DeliveryBody(ContentReference(url = "https://example.test/post"), listOf(ContentReference(assetId = asset.id)), null)
        val request = SubmitDelivery(UUID.randomUUID(), obligationId, content)
        val delivery = collaborations.submit(collaborationId, request).success()
        assertEquals(delivery.id, collaborations.submit(collaborationId, request).success().id)
        assertEquals(1L, collaborations.deliveries(collaborationId, "PENDING_VALIDATION", PageRequest()).success().total)

        authentication.signOut(); authentication.signIn(brandEmail, password).success()
        assertEquals("evidence.pdf", collaborations.asset(asset.id).success().filename)
        assertArrayEquals(raw, collaborations.download(asset.id).success())
        val correctionDeadline = clock.instant().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS)
        val review = ReviewDelivery(UUID.randomUUID(), delivery.currentSubmission.id, delivery.version, "Agregar el mensaje acordado", correctionDeadline)
        val rejectedDelivery = collaborations.reject(delivery.id, review).success()
        val incidentEvidence = collaborations.upload(collaborationId, UUID.randomUUID(), doc, true).success()
        val incident = collaborations.openIncident(collaborationId, OpenIncident(UUID.randomUUID(), IncidentCategory.DELIVERABLE,
            "Acordar un plazo con el creador", delivery.id, listOf(ContentReference(assetId = incidentEvidence.id)))).success()
        assertEquals(CollaborationStatus.DISPUTED, collaborations.collaboration(collaborationId).success().status)
        val extension = ResolutionTerms(ResolutionOutcome.EXTEND_CORRECTION, "Acordamos un plazo más amplio", emptyList(),
            delivery.id, rejectedDelivery.currentSubmission.id, rejectedDelivery.version, correctionDeadline.plusSeconds(3600))
        val proposed = collaborations.proposeResolution(incident.id, ProposeResolution(UUID.randomUUID(), incident.version, extension)).success()
        val proposalId = checkNotNull(proposed.currentResolution).id

        authentication.signOut(); authentication.signIn(thirdEmail, password).success()
        assertArrayEquals(raw, collaborations.download(incidentEvidence.id).success())
        assertEquals(incident.id, collaborations.incidents(collaborationId, null, PageRequest()).success().items.single().id)
        assertEquals(proposalId, collaborations.resolutions(incident.id, PageRequest()).success().items.single().id)
        val activeIncident = collaborations.incident(incident.id).success()
        val accepted = collaborations.decideResolution(incident.id, proposalId,
            DecideResolution(UUID.randomUUID(), activeIncident.version, ResolutionDecision.ACCEPT, null)).success()
        assertEquals("RESOLVED", accepted.status)
        val toCorrect = collaborations.delivery(delivery.id).success()
        assertEquals(extension.correctionDeadline, toCorrect.effectiveCorrectionDeadline)
        val revision = collaborations.revise(delivery.id, ReviseDelivery(UUID.randomUUID(), toCorrect.version,
            content.copy(notes = "Mensaje agregado"))).success()
        assertEquals(2, revision.currentSubmission.revision)

        authentication.signOut(); authentication.signIn(brandEmail, password).success()
        val approved = collaborations.approve(delivery.id, ReviewDelivery(UUID.randomUUID(), revision.currentSubmission.id,
            revision.version, null)).success()
        assertEquals("APPROVED", approved.status)
        val versions = collaborations.revisions(delivery.id, PageRequest()).success()
        assertEquals(2L, versions.total)
        assertEquals("REJECTED", versions.items.last().review?.decision)
        assertTrue(versions.items.last().correctionExtensions.isNotEmpty())
        val historical = collaborations.historyDetail(collaborationId).success()
        assertEquals(CollaborationStatus.COMPLETED, historical.collaboration.status)
        assertEquals(agreement.snapshot.termsVersion, historical.agreement.snapshot.termsVersion)
        assertEquals(collaborationId, collaborations.history(CollaborationStatus.COMPLETED, PageRequest()).success().items.single().id)
        assertTrue(collaborations.timeline(collaborationId, PageRequest()).success().items.isNotEmpty())
    }

    private fun <T> ApiResult<T>.success(): T {
        assertTrue("Expected success, got $this", this is ApiResult.Success)
        return (this as ApiResult.Success).value
    }
}
