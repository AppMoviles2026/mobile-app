package com.example.collabpro.features.campaign.infrastructure.remote

import com.example.collabpro.core.application.security.SessionCredentials
import retrofit2.Response
import retrofit2.http.*

internal interface ApplicantEvaluationApi {
    @GET("campaigns/{id}/applications")
    suspend fun list(@Tag session: SessionCredentials, @Path("id") campaign: String,
        @Query("page") page: Int, @Query("size") size: Int, @Query("status") status: String?): Response<PageDto<ApplicantDto>>

    @GET("campaigns/{id}/applications/{applicationId}")
    suspend fun detail(@Tag session: SessionCredentials, @Path("id") campaign: String,
        @Path("applicationId") application: String): Response<ApplicantDto>

    @POST("campaigns/{id}/applications/{applicationId}/decision")
    suspend fun decide(@Tag session: SessionCredentials, @Path("id") campaign: String,
        @Path("applicationId") application: String, @Body body: ApplicantDecisionDto): Response<ApplicantDto>
}

internal data class ApplicantDto(val application: ApplicationDto?, val creator: ApplicantProfileDto?)
internal data class ApplicantProfileDto(
    val id: String?, val name: String?, val biography: String?, val niche: String?,
    val audienceDescription: String?, val location: String?, val active: Boolean?, val socialAccounts: List<ApplicantSocialDto>?
)
internal data class ApplicantSocialDto(val id: String?, val platform: String?, val username: String?)
internal data class ApplicantDecisionDto(val decision: String, val reason: String?, val expectedVersion: Long)
