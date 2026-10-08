package com.example.collabpro.features.campaign.application.applications

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.domain.model.Application
import com.example.collabpro.features.campaign.domain.repositories.ApplicationRepository
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.util.UUID

/** No by-campaign endpoint exists in V1: scan all own pages, never mistake a failed/partial read for absence. */
class FindOwnApplication(private val repository: ApplicationRepository) {
    suspend operator fun invoke(creatorId: UUID, campaignId: UUID): ApiResult<Application?> {
        var index = 0; var total: Long? = null; val seen = mutableSetOf<UUID>()
        while (true) {
            coroutineContext.ensureActive()
            val response = repository.mine(PageRequest(index, 100))
            coroutineContext.ensureActive()
            when (response) {
                is ApiResult.Failure -> return response
                is ApiResult.Success -> {
                    val page = response.value
                    if (page.page != index || page.size != 100 || page.total < 0 || page.total > Int.MAX_VALUE ||
                        page.items.size.toLong() != (page.total - index.toLong() * 100).coerceIn(0L, 100L) || (total != null && total != page.total) ||
                        page.items.any { !it.belongsTo(creatorId) || !seen.add(it.id) } ||
                        (page.items.isEmpty() && index.toLong() * 100 < page.total)) return invalid()
                    total = page.total
                    val matches = page.items.filter { it.campaignId == campaignId }
                    if (matches.size > 1) return invalid()
                    matches.singleOrNull()?.let { return ApiResult.Success(it) }
                    if ((index.toLong() + 1) * 100 >= page.total) return ApiResult.Success(null)
                    index++
                }
            }
        }
    }
    private fun invalid() = ApiResult.Failure(ApiFailure(FailureKind.MALFORMED_RESPONSE,
        message = "No se pudo verificar toda la lista de postulaciones propias. Actualiza antes de enviar."))
}
