package com.example.collabpro.features.performance.infrastructure.remote

import com.example.collabpro.core.application.security.SessionCredentials
import com.example.collabpro.features.performance.domain.model.*
import java.time.Instant
import java.util.UUID
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

internal interface PerformanceApi {
    @GET("collaborations/{id}/metrics")
    suspend fun metrics(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<MetricsDto>

    @POST("collaborations/{id}/metrics/refresh")
    suspend fun refresh(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Body body: RefreshMetrics
    ): Response<RefreshDto>

    @GET("collaborations/{id}/metrics/snapshots")
    suspend fun snapshots(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<PageDto<Snapshot>>

    @GET("collaborations/{id}/evidence")
    suspend fun evidence(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("status") status: String?
    ): Response<PageDto<Evidence>>

    @GET("performance-evidence/{id}")
    suspend fun evidenceDetail(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID
    ): Response<Evidence>

    @POST("collaborations/{id}/evidence")
    suspend fun register(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Body body: RegisterEvidence
    ): Response<EvidenceDto>

    @POST("performance-evidence/{id}/verification")
    suspend fun review(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Body body: ReviewEvidence
    ): Response<EvidenceDto>

    @Multipart @POST("collaborations/{id}/evidence-assets")
    suspend fun upload(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Header("Idempotency-Key") key: UUID,
        @Part file: MultipartBody.Part
    ): Response<AssetDto>

    @GET("performance-assets/{id}")
    suspend fun asset(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID
    ): Response<AssetDto>

    @Streaming @GET("performance-assets/{id}/content")
    suspend fun download(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID
    ): Response<ResponseBody>

    @GET("attribution/destinations")
    suspend fun destinations(
        @Tag session: SessionCredentials
    ): Response<List<Destination>>

    @GET("collaborations/{id}/attribution")
    suspend fun attribution(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("start") start: Instant?,
        @Query("end") end: Instant?
    ): Response<AttributionDto>

    @POST("collaborations/{id}/attribution")
    suspend fun create(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Body body: CreateMechanism
    ): Response<MechanismDto>

    @POST("attribution/{id}/status")
    suspend fun change(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Body body: ChangeMechanism
    ): Response<MechanismDto>

    @POST("attribution/{id}/interactions")
    suspend fun record(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Body body: RecordInteraction
    ): Response<InteractionDto>

    @GET("attribution/{id}/interactions")
    suspend fun interactions(
        @Tag session: SessionCredentials,
        @Path("id") id: UUID,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("start") start: Instant?,
        @Query("end") end: Instant?
    ): Response<PageDto<Interaction>>
}
