package com.example.collabpro.features.campaign.application.dashboard

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.applications.belongsTo
import com.example.collabpro.features.campaign.domain.repositories.*
import java.util.UUID

/** A total across all statuses, not an invented active/pending count from one page. */
class LoadOwnActivityTotal(private val campaigns: CampaignRepository, private val applications: ApplicationRepository) {
    suspend fun campaigns(brandId: UUID): ApiResult<Long> = when (val result = campaigns.mine(PageRequest(0, 1))) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> if (valid(result.value) && result.value.items.all { it.brandId == brandId }) ApiResult.Success(result.value.total) else invalid()
    }
    suspend fun applications(creatorId: UUID): ApiResult<Long> = when (val result = applications.mine(PageRequest(0, 1))) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> if (valid(result.value) && result.value.items.all { it.belongsTo(creatorId) }) ApiResult.Success(result.value.total) else invalid()
    }
    private fun valid(page: Page<*>) = page.page == 0 && page.size == 1 && page.total >= 0 && page.items.size.toLong() == minOf(page.total, 1)
    private fun invalid() = ApiResult.Failure(ApiFailure(FailureKind.MALFORMED_RESPONSE, message = "No se pudo verificar el resumen de tu cuenta."))
}
