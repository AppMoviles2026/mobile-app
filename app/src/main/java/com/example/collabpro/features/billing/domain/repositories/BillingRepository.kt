package com.example.collabpro.features.billing.domain.repositories

import com.example.collabpro.core.domain.ApiResult
import com.example.collabpro.core.domain.Page
import com.example.collabpro.core.domain.PageRequest
import com.example.collabpro.features.billing.domain.model.*
import java.util.UUID

interface BillingRepository {
    suspend fun methods(id: UUID, page: PageRequest): ApiResult<Page<BillingMethod>>
    suspend fun beginLink(id: UUID, body: BeginBilling): ApiResult<BillingAuthorization>
    suspend fun authorization(id: UUID): ApiResult<BillingAuthorization>
    suspend fun cancelLink(id: UUID): ApiResult<BillingAuthorization>
    suspend fun validateMethod(id: UUID, body: ValidateBillingMethod): ApiResult<BillingReadiness>
    suspend fun plans(): ApiResult<List<SubscriptionPlan>>
    suspend fun beginSubscription(id: UUID, body: BeginSubscription): ApiResult<BillingSubscription>
    suspend fun currentSubscription(id: UUID): ApiResult<BillingSubscription>
    suspend fun subscriptionHistory(id: UUID, page: PageRequest): ApiResult<Page<BillingSubscription>>
    suspend fun subscription(id: UUID): ApiResult<BillingSubscription>
    suspend fun refreshSubscription(id: UUID): ApiResult<BillingSubscription>
    suspend fun resumeSubscription(id: UUID, body: ResumeSubscription): ApiResult<BillingSubscription>
    suspend fun cancelSubscription(id: UUID): ApiResult<BillingSubscription>
    suspend fun compensation(id: UUID): ApiResult<BillingCompensation>
    suspend fun compensationById(id: UUID): ApiResult<BillingCompensation>
    suspend fun beginPayment(id: UUID, body: BeginCompensation): ApiResult<BillingCompensation>
    suspend fun confirmPayment(id: UUID, body: ConfirmCompensation): ApiResult<BillingCompensation>
    suspend fun refreshPayment(id: UUID): ApiResult<BillingCompensation>
    suspend fun compensationHistory(id: UUID, page: PageRequest): ApiResult<Page<CompensationAudit>>
}
