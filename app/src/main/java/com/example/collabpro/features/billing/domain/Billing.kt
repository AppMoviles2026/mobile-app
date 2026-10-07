package com.example.collabpro.features.billing.domain

data class Plan(val name: String, val price: String, val description: String)
data class Compensation(val amount: String, val type: String, val status: String)

interface BillingRepository {
    fun samplePlan(): Plan
    fun sampleCompensation(): Compensation
}
