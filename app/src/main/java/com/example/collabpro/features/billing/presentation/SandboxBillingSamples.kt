package com.example.collabpro.features.billing.presentation

import androidx.compose.runtime.Composable
import com.example.collabpro.core.designsystem.*
import com.example.collabpro.features.billing.domain.model.*
import com.example.collabpro.features.billing.presentation.sandbox.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Fixtures for Android Studio previews. No runtime route accesses this object. */
internal object SandboxBillingSamples {
    private val date = Instant.parse("2026-11-24T18:00:00Z")
    private val id = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val plan = SubscriptionPlan(id, "ESSENTIAL", "Esencial", "Seguimiento de campañas",
        BigDecimal("5.00"), "USD", "MONTH", listOf("Resumen de actividad"), true, true, null)

    @Composable
    fun Methods() = Page("Medios de pago", eyebrow = "PAYPAL SANDBOX") {
        item {
            Panel("PayPal · d•••") {
                MethodFacts(BillingMethod(id, BillingPurpose.PAYMENT, "ACTIVE", "PAYPAL", "SANDBOX", "d•••", date, 1))
            }
        }
    }

    @Composable
    fun Authorization() = Page("Cuenta receptora", eyebrow = "PAYPAL SANDBOX") {
        item {
            Panel("Vinculación PayPal") {
                AuthorizationFacts(BillingAuthorization(id, BillingPurpose.PAYOUT, "PAYPAL", "SANDBOX", "PENDING_APPROVAL",
                    date.plusSeconds(1800), null, null, null, 1))
                Action("Abrir PayPal Sandbox", {}, enabled = false)
            }
        }
    }

    @Composable
    fun Plans() = Page("Planes y suscripción", eyebrow = "PAYPAL SANDBOX") {
        item { Panel(plan.name) { PlanFacts(plan) } }
    }

    @Composable
    fun Subscription() = Page("Planes y suscripción", eyebrow = "PAYPAL SANDBOX") {
        item {
            Panel("Suscripción actual") {
                SubscriptionFacts(BillingSubscription(id, id, plan.name, plan.amount, plan.currency, "PAYMENT_PENDING",
                    "APPROVED", "PAYPAL", "SANDBOX", id, false, emptyList(), null, null, null, date, null, 1))
            }
        }
    }

    @Composable
    fun Compensation() = Page("Compensación", eyebrow = "PAYPAL SANDBOX") {
        item {
            CompensationFacts(BillingCompensation(id, id, id, "CASH", BigDecimal("5.00"), "USD",
                "Una pieza de contenido aprobada", "PAYOUT_PENDING", true, true, 0, false,
                date, null, "COMPLETED", "PENDING", "PAYPAL", "SANDBOX", null, null, null, 1, emptyList()))
        }
    }
}
