package com.example.collabpro.features.billing.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class BillingPurpose { PAYMENT, PAYOUT }
data class BillingMethod(
    val methodId: UUID, val purpose: BillingPurpose, val status: String,
    val provider: String, val environment: String, val maskedLabel: String?, val linkedAt: Instant, val version: Long
)
data class BillingAuthorization(
    val authorizationId: UUID, val purpose: BillingPurpose, val provider: String, val environment: String,
    val status: String, val expiresAt: Instant, val approvalUrl: String?, val methodId: UUID?, val errorCode: String?, val version: Long
) {
    val terminal: Boolean get() = status in setOf("LINKED", "CANCELLED", "FAILED", "EXPIRED")
    override fun toString() = "BillingAuthorization(status=$status)"
}
data class BillingReadiness(val methodId: UUID, val purpose: BillingPurpose, val provider: String, val environment: String)
data class SubscriptionPlan(
    val planId: UUID, val code: String, val name: String, val description: String, val amount: BigDecimal,
    val currency: String, val interval: String, val benefits: List<String>, val demonstration: Boolean,
    val available: Boolean, val errorCode: String?
)
data class BillingSubscription(
    val subscriptionId: UUID?, val planId: UUID?, val planName: String?, val amount: BigDecimal?, val currency: String?,
    val status: String, val providerStatus: String?, val provider: String, val environment: String,
    val methodId: UUID?, val benefitsActive: Boolean, val benefits: List<String>, val approvalUrl: String?,
    val consentExpiresAt: Instant?, val paidThrough: Instant?, val lastCheckedAt: Instant?, val errorCode: String?, val version: Long
) {
    val canStart: Boolean get() = status in setOf("NONE", "CANCELLED", "EXPIRED", "FAILED")
    override fun toString() = "BillingSubscription(status=$status)"
}
data class BillingCompensation(
    val compensationId: UUID, val collaborationId: UUID, val agreementId: UUID, val type: String,
    val amount: BigDecimal?, val currency: String?, val description: String?, val status: String,
    val paymentApplicable: Boolean, val eligible: Boolean, val openIncidentCount: Int, val wasPaid: Boolean,
    val fundedAt: Instant?, val paidAt: Instant?, val fundingStatus: String?, val payoutStatus: String?,
    val provider: String, val environment: String, val approvalUrl: String?, val errorCode: String?,
    val unavailableReason: String?, val version: Long, val availableActions: List<String>
) {
    override fun toString() = "BillingCompensation(status=$status)"
}
data class CompensationAudit(val id: UUID, val operationId: UUID?, val actor: UUID?, val type: String, val time: Instant)

data class BeginBilling(val requestId: UUID, val client: String = "ANDROID")
data class ValidateBillingMethod(val methodId: UUID)
data class BeginSubscription(val requestId: UUID, val planId: UUID, val methodId: UUID, val client: String = "ANDROID")
data class ResumeSubscription(val expectedVersion: Long)
data class BeginCompensation(val requestId: UUID, val methodId: UUID?, val expectedVersion: Long, val client: String = "ANDROID")
data class ConfirmCompensation(val requestId: UUID, val expectedVersion: Long)
