package com.example.collabpro.features.collaboration.infrastructure.remote

import com.example.collabpro.core.application.security.SessionCredentials
import com.example.collabpro.features.collaboration.domain.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*
import java.util.UUID

internal interface CollaborationApi {
    @GET("agreements/mine")
    suspend fun agreements(
        @Tag session: SessionCredentials,
        @Query("status") status: String?,
        @Query("page") page: Int, @Query("size") size: Int
    ): Response<CollaborationPageDto<AgreementRecord>>

    @GET("agreements/{id}")
    suspend fun agreement(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID
    ): Response<AgreementRecord>

    @GET("collaborations/mine")
    suspend fun collaborations(
        @Tag session: SessionCredentials,
        @Query("status") status: String?,
        @Query("page") page: Int, @Query("size") size: Int
    ): Response<CollaborationPageDto<CollaborationRecord>>

    @GET("collaborations/{id}")
    suspend fun collaboration(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID
    ): Response<CollaborationRecord>

    @GET("collaborations/{id}/deliverables")
    suspend fun deliveries(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID, @Query("status") status: String?,
        @Query("page") page: Int, @Query("size") size: Int
    ): Response<CollaborationPageDto<DeliveryRecord>>

    @GET("deliverables/{id}")
    suspend fun delivery(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID
    ): Response<DeliveryRecord>

    @GET("deliverables/{id}/versions")
    suspend fun revisions(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Query("page") page: Int, @Query("size") size: Int
    ): Response<CollaborationPageDto<DeliveryRevision>>

    @GET("collaborations/{id}/incidents")
    suspend fun incidents(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID, @Query("status") status: String?,
        @Query("page") page: Int, @Query("size") size: Int
    ): Response<CollaborationPageDto<IncidentRecord>>

    @GET("incidents/{id}")
    suspend fun incident(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID
    ): Response<IncidentRecord>

    @GET("incidents/{id}/resolutions")
    suspend fun resolutions(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Query("page") page: Int, @Query("size") size: Int
    ): Response<CollaborationPageDto<ResolutionRecord>>

    @GET("collaborations/history")
    suspend fun history(
        @Tag session: SessionCredentials,
        @Query("status") status: String?,
        @Query("page") page: Int, @Query("size") size: Int
    ): Response<CollaborationPageDto<CollaborationRecord>>

    @GET("collaborations/history/{id}")
    suspend fun historyDetail(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID
    ): Response<HistoryRecord>

    @GET("collaborations/{id}/history")
    suspend fun timeline(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Query("page") page: Int, @Query("size") size: Int
    ): Response<CollaborationPageDto<HistoryEntry>>

    @GET("collaboration-assets/{id}")
    suspend fun asset(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID
    ): Response<AssetEnvelope>

    @POST("agreements")
    suspend fun proposeAgreement(
        @Tag session: SessionCredentials, @Body body: ProposeAgreement
    ): Response<AgreementRecord>

    @POST("agreements/{id}/decisions")
    suspend fun decideAgreement(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID, @Body body: DecideAgreement
    ): Response<AgreementRecord>

    @POST("collaborations")
    suspend fun recoverCollaboration(
        @Tag session: SessionCredentials, @Body body: RecoverCollaboration
    ): Response<CollaborationRecord>

    @POST("collaborations/{id}/deliverables")
    suspend fun submit(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID, @Body body: SubmitDelivery
    ): Response<DeliveryResult>

    @POST("deliverables/{id}/versions")
    suspend fun revise(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID, @Body body: ReviseDelivery
    ): Response<DeliveryResult>

    @POST("deliverables/{id}/validation")
    suspend fun approve(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID, @Body body: ReviewDelivery
    ): Response<DeliveryResult>

    @POST("deliverables/{id}/rejection")
    suspend fun reject(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID, @Body body: ReviewDelivery
    ): Response<DeliveryResult>

    @POST("collaborations/{id}/incidents")
    suspend fun openIncident(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID, @Body body: OpenIncident
    ): Response<IncidentResult>

    @POST("incidents/{id}/resolutions")
    suspend fun proposeResolution(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID, @Body body: ProposeResolution
    ): Response<IncidentResult>

    @POST("incidents/{id}/resolutions/{resolutionId}/decisions")
    suspend fun decideResolution(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID, @Path("resolutionId") resolutionId: UUID, @Body body: DecideResolution
    ): Response<IncidentResult>

    @Multipart @POST("collaborations/{id}/assets")
    suspend fun upload(@Tag session: SessionCredentials, @Path("id") id: UUID,
        @Header("Idempotency-Key") key: UUID, @Part file: MultipartBody.Part): Response<AssetEnvelope>

    @Multipart @POST("collaborations/{id}/incident-assets")
    suspend fun uploadIncident(@Tag session: SessionCredentials, @Path("id") id: UUID,
        @Header("Idempotency-Key") key: UUID, @Part file: MultipartBody.Part): Response<AssetEnvelope>

    @Streaming @GET("collaboration-assets/{id}/content")
    suspend fun download(@Tag session: SessionCredentials, @Path("id") id: UUID): Response<ResponseBody>
}

internal data class CollaborationPageDto<T>(val items: List<T>?, val total: Long?, val page: Int?, val size: Int?)
internal data class DeliveryResult(val deliverable: DeliveryRecord, val affectedSubmissionId: UUID)
internal data class IncidentResult(val incident: IncidentRecord, val affectedResolutionId: UUID?)
internal data class AssetEnvelope(val asset: AssetRecord)
