package com.example.collabpro.features.collaboration.infrastructure.remote

import com.example.collabpro.core.domain.Page
import com.example.collabpro.core.domain.PageRequest
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.collaboration.domain.model.*
import com.example.collabpro.features.collaboration.domain.repositories.CollaborationRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

internal class RemoteCollaborations(private val api: CollaborationApi, private val executor: ApiExecutor) : CollaborationRepository {
    override suspend fun agreements(status: String?, page: PageRequest) =
        executor.protectedCall({ api.agreements(it, status, page.page, page.size) }) { it.toDomain(page) }

    override suspend fun agreement(id: UUID) =
        executor.protectedCall({ api.agreement(it, id) }) { it.verified { it.id == id } }

    override suspend fun collaborations(status: CollaborationStatus?, page: PageRequest) =
        executor.protectedCall({ api.collaborations(it, status?.name, page.page, page.size) }) { it.toDomain(page) }

    override suspend fun collaboration(id: UUID) =
        executor.protectedCall({ api.collaboration(it, id) }) { it.verified { it.id == id } }

    override suspend fun deliveries(id: UUID, status: String?, page: PageRequest) =
        executor.protectedCall({ api.deliveries(it, id, status, page.page, page.size) }) { it.toDomain(page).verified { page -> page.items.all { item -> item.collaborationId == id } } }

    override suspend fun delivery(id: UUID) =
        executor.protectedCall({ api.delivery(it, id) }) { it.verified { it.id == id } }

    override suspend fun revisions(id: UUID, page: PageRequest) =
        executor.protectedCall({ api.revisions(it, id, page.page, page.size) }) { it.toDomain(page) }

    override suspend fun incidents(id: UUID, status: String?, page: PageRequest) =
        executor.protectedCall({ api.incidents(it, id, status, page.page, page.size) }) { it.toDomain(page).verified { page -> page.items.all { item -> item.collaborationId == id } } }

    override suspend fun incident(id: UUID) =
        executor.protectedCall({ api.incident(it, id) }) { it.verified { it.id == id } }

    override suspend fun resolutions(id: UUID, page: PageRequest) =
        executor.protectedCall({ api.resolutions(it, id, page.page, page.size) }) { it.toDomain(page) }

    override suspend fun history(status: CollaborationStatus?, page: PageRequest) =
        executor.protectedCall({ api.history(it, status?.name, page.page, page.size) }) { it.toDomain(page) }

    override suspend fun historyDetail(id: UUID) =
        executor.protectedCall({ api.historyDetail(it, id) }) { it.verified { it.collaboration.id == id && it.agreement.id == it.collaboration.agreementId } }

    override suspend fun timeline(id: UUID, page: PageRequest) =
        executor.protectedCall({ api.timeline(it, id, page.page, page.size) }) { it.toDomain(page).verified { page -> page.items.all { item -> item.collaborationId == id } } }

    override suspend fun asset(id: UUID) =
        executor.protectedCall({ api.asset(it, id) }) { it.verified { it.asset.id == id }.asset }

    override suspend fun proposeAgreement(body: ProposeAgreement) =
        executor.protectedCall({ api.proposeAgreement(it, body) }) { it.verified { it.snapshot.applicationId == body.applicationId } }

    override suspend fun decideAgreement(id: UUID, body: DecideAgreement) =
        executor.protectedCall({ api.decideAgreement(it, id, body) }) { it.verified { it.id == id && it.snapshot.termsVersion == body.termsVersion } }

    override suspend fun recoverCollaboration(body: RecoverCollaboration) =
        executor.protectedCall({ api.recoverCollaboration(it, body) }) { it.verified { it.agreementId == body.agreementId } }

    override suspend fun submit(id: UUID, body: SubmitDelivery) =
        executor.protectedCall({ api.submit(it, id, body) }) { it.verified { it.deliverable.collaborationId == id && it.deliverable.obligationId == body.obligationId }.deliverable }

    override suspend fun revise(id: UUID, body: ReviseDelivery) =
        executor.protectedCall({ api.revise(it, id, body) }) { it.verified { it.deliverable.id == id }.deliverable }

    override suspend fun approve(id: UUID, body: ReviewDelivery) =
        executor.protectedCall({ api.approve(it, id, body) }) { it.verified { it.deliverable.id == id && it.affectedSubmissionId == body.submissionId }.deliverable }

    override suspend fun reject(id: UUID, body: ReviewDelivery) =
        executor.protectedCall({ api.reject(it, id, body) }) { it.verified { it.deliverable.id == id && it.affectedSubmissionId == body.submissionId }.deliverable }

    override suspend fun openIncident(id: UUID, body: OpenIncident) =
        executor.protectedCall({ api.openIncident(it, id, body) }) { it.verified { it.incident.collaborationId == id }.incident }

    override suspend fun proposeResolution(id: UUID, body: ProposeResolution) =
        executor.protectedCall({ api.proposeResolution(it, id, body) }) { it.verified { it.incident.id == id }.incident }

    override suspend fun decideResolution(id: UUID, resolutionId: UUID, body: DecideResolution) =
        executor.protectedCall({ api.decideResolution(it, id, resolutionId, body) }) { it.verified { it.incident.id == id && it.affectedResolutionId == resolutionId }.incident }

    override suspend fun upload(id: UUID, requestId: UUID, document: UploadDocument, incident: Boolean) =
        executor.protectedCall({ session ->
            val part = MultipartBody.Part.createFormData("file", document.name,
                document.bytes.toRequestBody(document.mediaType.toMediaType()))
            if (incident) api.uploadIncident(session, id, requestId, part) else api.upload(session, id, requestId, part)
        }) { it.asset.verified { file -> file.collaborationId == id } }

    override suspend fun download(id: UUID) = executor.protectedCall({ api.download(it, id) }) { response ->
        response.use {
            val max = 50L * 1024 * 1024
            val stream = it.source()
            if (it.contentLength() > max || stream.request(max + 1)) throw InvalidApiResponse("Attachment too large")
            stream.readByteArray()
        }
    }
}

private fun <T> T.verified(check: (T) -> Boolean): T {
    if (!check(this)) throw InvalidApiResponse("Collaboration response does not match request")
    return this
}

private fun <T> CollaborationPageDto<T>.toDomain(request: PageRequest): Page<T> {
    val values = items.required("items")
    val count = total.required("total")
    if (page != request.page || size != request.size || count < values.size || values.size > request.size)
        throw InvalidApiResponse("Invalid collaboration page")
    return Page(values, count, request.page, request.size)
}
