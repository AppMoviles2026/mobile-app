package com.example.collabpro.features.campaign.infrastructure.remote

import com.example.collabpro.core.application.security.SessionCredentials
import retrofit2.Response
import retrofit2.http.*

internal interface CampaignApi {
    @POST("campaigns") suspend fun create(@Tag session: SessionCredentials, @Header("Idempotency-Key") key: String, @Body body: CreateCampaignDto): Response<CampaignDto>
    @PUT("campaigns/{id}/conditions") suspend fun conditions(@Tag session: SessionCredentials, @Path("id") id: String, @Body body: ConditionsDto): Response<CampaignDto>
    @POST("campaigns/{id}/publication") suspend fun publish(@Tag session: SessionCredentials, @Path("id") id: String): Response<CampaignDto>
    @GET("campaigns/mine") suspend fun mine(@Tag session: SessionCredentials, @Query("page") page: Int, @Query("size") size: Int): Response<PageDto<CampaignDto>>
    @GET("campaigns/published") suspend fun published(@Tag session: SessionCredentials, @Query("page") page: Int, @Query("size") size: Int): Response<PageDto<CampaignDto>>
    @GET("campaigns") suspend fun search(
        @Tag session: SessionCredentials, @Query("q") query: String?, @Query("category") category: String?,
        @Query("location") location: String?, @Query("compensationType") compensationType: String?,
        @Query("page") page: Int, @Query("size") size: Int
    ): Response<PageDto<CampaignDto>>
    @GET("campaigns/{id}") suspend fun details(@Tag session: SessionCredentials, @Path("id") id: String): Response<CampaignDto>
    @DELETE("campaigns/{id}") suspend fun discard(@Tag session: SessionCredentials, @Path("id") id: String): Response<Unit>
    @POST("campaigns/{id}/closure") suspend fun close(@Tag session: SessionCredentials, @Path("id") id: String): Response<CampaignDto>
}

internal interface ApplicationApi {
    @POST("campaigns/{id}/applications") suspend fun submit(
        @Tag session: SessionCredentials, @Path("id") campaignId: String, @Header("Idempotency-Key") key: String, @Body body: SubmitApplicationDto
    ): Response<ApplicationDto>
    @GET("applications/mine") suspend fun mine(@Tag session: SessionCredentials, @Query("page") page: Int, @Query("size") size: Int): Response<PageDto<ApplicationDto>>
    @GET("applications/{id}") suspend fun details(@Tag session: SessionCredentials, @Path("id") id: String): Response<ApplicationDto>
    @PUT("applications/{id}") suspend fun update(@Tag session: SessionCredentials, @Path("id") id: String, @Body body: UpdateApplicationDto): Response<ApplicationDto>
    @POST("applications/{id}/cancellation") suspend fun cancel(@Tag session: SessionCredentials, @Path("id") id: String, @Body body: CancelApplicationDto): Response<ApplicationDto>
}
