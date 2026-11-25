package com.example.collabpro.features.billing.infrastructure.remote

import com.example.collabpro.core.application.security.SessionCredentials
import com.example.collabpro.features.billing.domain.model.*
import java.util.UUID
import retrofit2.Response
import retrofit2.http.*

internal interface BillingApi {
    @GET("user/{id}/billing")
    suspend fun methods(
        @Tag session: SessionCredentials, @Path("id") id: UUID,
        @Query("page") page: Int, @Query("size") size: Int
    ): Response<BillingPageDto<BillingMethod>>

    @POST("user/{id}/billing")
    suspend fun beginLink(
        @Tag session: SessionCredentials, @Path("id") id: UUID, @Body body: BeginBilling
    ): Response<BillingAuthorization>

    @GET("billing/authorizations/{id}")
    suspend fun authorization(
        @Tag session: SessionCredentials, @Path("id") id: UUID
    ): Response<BillingAuthorization>

    @POST("billing/authorizations/{id}/cancellation")
    suspend fun cancelLink(
        @Tag session: SessionCredentials, @Path("id") id: UUID
    ): Response<BillingAuthorization>

    @POST("user/{id}/billing/validation")
    suspend fun validateMethod(
        @Tag session: SessionCredentials, @Path("id") id: UUID, @Body body: ValidateBillingMethod
    ): Response<BillingReadiness>

    @GET("subscription-plans")
    suspend fun plans(
        @Tag session: SessionCredentials
    ): Response<List<SubscriptionPlan>>

    @POST("user/{id}/subscription")
    suspend fun beginSubscription(
        @Tag session: SessionCredentials, @Path("id") id: UUID, @Body body: BeginSubscription
    ): Response<BillingSubscription>

    @GET("user/{id}/subscription")
    suspend fun currentSubscription(
        @Tag session: SessionCredentials, @Path("id") id: UUID
    ): Response<BillingSubscription>

    @GET("user/{id}/subscriptions")
    suspend fun subscriptionHistory(
        @Tag session: SessionCredentials, @Path("id") id: UUID,
        @Query("page") page: Int, @Query("size") size: Int
    ): Response<BillingPageDto<BillingSubscription>>

    @GET("subscriptions/{id}")
    suspend fun subscription(
        @Tag session: SessionCredentials, @Path("id") id: UUID
    ): Response<BillingSubscription>

    @POST("subscriptions/{id}/refresh")
    suspend fun refreshSubscription(
        @Tag session: SessionCredentials, @Path("id") id: UUID
    ): Response<BillingSubscription>

    @POST("subscriptions/{id}/resume-approval")
    suspend fun resumeSubscription(
        @Tag session: SessionCredentials, @Path("id") id: UUID, @Body body: ResumeSubscription
    ): Response<BillingSubscription>

    @POST("subscriptions/{id}/cancellation")
    suspend fun cancelSubscription(
        @Tag session: SessionCredentials, @Path("id") id: UUID
    ): Response<BillingSubscription>

    @GET("collaborations/{id}/compensation")
    suspend fun compensation(
        @Tag session: SessionCredentials, @Path("id") id: UUID
    ): Response<BillingCompensation>

    @GET("compensations/{id}")
    suspend fun compensationById(
        @Tag session: SessionCredentials, @Path("id") id: UUID
    ): Response<BillingCompensation>

    @POST("collaborations/{id}/payment")
    suspend fun beginPayment(
        @Tag session: SessionCredentials, @Path("id") id: UUID, @Body body: BeginCompensation
    ): Response<BillingCompensation>

    @POST("collaborations/{id}/payment/confirmation")
    suspend fun confirmPayment(
        @Tag session: SessionCredentials, @Path("id") id: UUID, @Body body: ConfirmCompensation
    ): Response<BillingCompensation>

    @POST("collaborations/{id}/payment/refresh")
    suspend fun refreshPayment(
        @Tag session: SessionCredentials, @Path("id") id: UUID
    ): Response<BillingCompensation>

    @GET("collaborations/{id}/compensation/history")
    suspend fun compensationHistory(
        @Tag session: SessionCredentials, @Path("id") id: UUID,
        @Query("page") page: Int, @Query("size") size: Int
    ): Response<BillingPageDto<CompensationAudit>>
}

internal data class BillingPageDto<T>(val items: List<T>?, val total: Long?, val page: Int?, val size: Int?)
