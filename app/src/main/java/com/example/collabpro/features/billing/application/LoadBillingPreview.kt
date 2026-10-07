package com.example.collabpro.features.billing.application

import com.example.collabpro.features.billing.domain.BillingRepository

class LoadBillingPreview(private val repository: BillingRepository) {
    fun plan() = repository.samplePlan()
    fun compensation() = repository.sampleCompensation()
}
