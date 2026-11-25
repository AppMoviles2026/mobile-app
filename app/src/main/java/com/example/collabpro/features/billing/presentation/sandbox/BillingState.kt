package com.example.collabpro.features.billing.presentation.sandbox

import com.example.collabpro.core.domain.ApiFailure
import com.example.collabpro.core.domain.Page
import com.example.collabpro.features.billing.domain.model.*
import java.time.Instant
import java.util.UUID

internal enum class BillingTab { METHODS, SUBSCRIPTION, COMPENSATION }
internal data class BillingDestination(val tab: BillingTab, val collaborationId: UUID? = null)
internal data class BillingState(
    val owner: UUID? = null, val expiresAt: Instant? = null,
    val destination: BillingDestination? = null,
    val methods: Page<BillingMethod>? = null,
    val usable: List<BillingMethod> = emptyList(),
    val authorization: BillingAuthorization? = null,
    val plans: List<SubscriptionPlan>? = null,
    val subscription: BillingSubscription? = null,
    val historicalSubscription: BillingSubscription? = null,
    val subscriptions: Page<BillingSubscription>? = null,
    val compensation: BillingCompensation? = null,
    val audit: Page<CompensationAudit>? = null,
    val loading: Boolean = false,
    val recovering: Boolean = false,
    val failure: ApiFailure? = null,
    val notice: String? = null,
    val navigation: BillingDestination? = null
)
