package com.example.collabpro.features.collaboration.domain.model

import java.time.Instant
import java.util.UUID

data class ProposeAgreement(val applicationId: UUID)
data class DecideAgreement(val termsVersion: UUID, val decision: AgreementDecision)
data class RecoverCollaboration(val agreementId: UUID)
data class SubmitDelivery(val requestId: UUID, val obligationId: UUID, val content: DeliveryBody)
data class ReviseDelivery(val requestId: UUID, val expectedVersion: Long, val content: DeliveryBody)
data class ReviewDelivery(
    val requestId: UUID, val submissionId: UUID, val expectedVersion: Long, val observations: String?,
    val correctionDeadline: Instant? = null
)
data class OpenIncident(
    val requestId: UUID, val category: IncidentCategory, val description: String,
    val deliverableId: UUID?, val evidence: List<ContentReference>
)
data class ProposeResolution(val requestId: UUID, val expectedVersion: Long, val terms: ResolutionTerms)
data class DecideResolution(val requestId: UUID, val expectedVersion: Long, val decision: ResolutionDecision, val reason: String?)
data class UploadDocument(val name: String, val mediaType: String, val bytes: ByteArray)
