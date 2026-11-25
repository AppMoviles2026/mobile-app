package com.example.collabpro.features.collaboration.domain.repositories

import com.example.collabpro.core.domain.ApiResult
import com.example.collabpro.core.domain.Page
import com.example.collabpro.core.domain.PageRequest
import com.example.collabpro.features.collaboration.domain.model.*
import java.util.UUID

interface CollaborationRepository {
    suspend fun agreements(status: String?, page: PageRequest): ApiResult<Page<AgreementRecord>>
    suspend fun agreement(id: UUID): ApiResult<AgreementRecord>
    suspend fun collaborations(status: CollaborationStatus?, page: PageRequest): ApiResult<Page<CollaborationRecord>>
    suspend fun collaboration(id: UUID): ApiResult<CollaborationRecord>
    suspend fun deliveries(id: UUID, status: String?, page: PageRequest): ApiResult<Page<DeliveryRecord>>
    suspend fun delivery(id: UUID): ApiResult<DeliveryRecord>
    suspend fun revisions(id: UUID, page: PageRequest): ApiResult<Page<DeliveryRevision>>
    suspend fun incidents(id: UUID, status: String?, page: PageRequest): ApiResult<Page<IncidentRecord>>
    suspend fun incident(id: UUID): ApiResult<IncidentRecord>
    suspend fun resolutions(id: UUID, page: PageRequest): ApiResult<Page<ResolutionRecord>>
    suspend fun history(status: CollaborationStatus?, page: PageRequest): ApiResult<Page<CollaborationRecord>>
    suspend fun historyDetail(id: UUID): ApiResult<HistoryRecord>
    suspend fun timeline(id: UUID, page: PageRequest): ApiResult<Page<HistoryEntry>>
    suspend fun asset(id: UUID): ApiResult<AssetRecord>
    suspend fun proposeAgreement(body: ProposeAgreement): ApiResult<AgreementRecord>
    suspend fun decideAgreement(id: UUID, body: DecideAgreement): ApiResult<AgreementRecord>
    suspend fun recoverCollaboration(body: RecoverCollaboration): ApiResult<CollaborationRecord>
    suspend fun submit(id: UUID, body: SubmitDelivery): ApiResult<DeliveryRecord>
    suspend fun revise(id: UUID, body: ReviseDelivery): ApiResult<DeliveryRecord>
    suspend fun approve(id: UUID, body: ReviewDelivery): ApiResult<DeliveryRecord>
    suspend fun reject(id: UUID, body: ReviewDelivery): ApiResult<DeliveryRecord>
    suspend fun openIncident(id: UUID, body: OpenIncident): ApiResult<IncidentRecord>
    suspend fun proposeResolution(id: UUID, body: ProposeResolution): ApiResult<IncidentRecord>
    suspend fun decideResolution(id: UUID, resolutionId: UUID, body: DecideResolution): ApiResult<IncidentRecord>
    suspend fun upload(id: UUID, requestId: UUID, document: UploadDocument, incident: Boolean): ApiResult<AssetRecord>
    suspend fun download(id: UUID): ApiResult<ByteArray>
}
