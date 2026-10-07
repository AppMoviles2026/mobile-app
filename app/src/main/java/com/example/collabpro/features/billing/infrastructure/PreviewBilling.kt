package com.example.collabpro.features.billing.infrastructure

import com.example.collabpro.features.billing.domain.*

object PreviewBilling : BillingRepository {
    override fun samplePlan() = Plan("Esencial", "Por definir", "Para comenzar con campañas pequeñas.")
    override fun sampleCompensation() = Compensation("S/ 180 + productos", "Canje + pago", "Pendiente")
}
