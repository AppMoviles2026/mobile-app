package com.example.collabpro.features.billing.infrastructure.remote

import com.example.collabpro.core.domain.Page
import com.example.collabpro.core.domain.PageRequest
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.billing.domain.model.*
import com.example.collabpro.features.billing.domain.repositories.BillingRepository
import java.util.UUID

internal class RemoteBilling(private val api: BillingApi, private val executor: ApiExecutor) : BillingRepository {
    override suspend fun methods(id: UUID, page: PageRequest) =
        executor.protectedCall({ api.methods(it, id, page.page, page.size) }) { it.toPage(page) { m -> m.sandbox() } }

    override suspend fun beginLink(id: UUID, body: BeginBilling) =
        executor.protectedCall({ api.beginLink(it, id, body) }) { it.sandbox() }

    override suspend fun authorization(id: UUID) =
        executor.protectedCall({ api.authorization(it, id) }) { it.sandbox().verify { it.authorizationId == id } }

    override suspend fun cancelLink(id: UUID) =
        executor.protectedCall({ api.cancelLink(it, id) }) { it.sandbox().verify { it.authorizationId == id } }

    override suspend fun validateMethod(id: UUID, body: ValidateBillingMethod) =
        executor.protectedCall({ api.validateMethod(it, id, body) }) { readiness ->
            readiness.purpose.required("purpose")
            readiness.verify { it.provider == "PAYPAL" && it.environment == "SANDBOX" && it.methodId == body.methodId }
        }

    override suspend fun plans() =
        executor.protectedCall({ api.plans(it) }) { plans ->
            plans.onEach { plan ->
                plan.planId.required("planId")
                plan.name.required("name")
                plan.description.required("description")
                plan.amount.required("amount")
                plan.currency.required("currency")
                plan.interval.required("interval")
                plan.benefits.required("benefits")
            }
        }

    override suspend fun beginSubscription(id: UUID, body: BeginSubscription) =
        executor.protectedCall({ api.beginSubscription(it, id, body) }) { it.sandbox().verify { it.planId == body.planId && it.methodId == body.methodId } }

    override suspend fun currentSubscription(id: UUID) =
        executor.protectedCall({ api.currentSubscription(it, id) }) { it.sandbox() }

    override suspend fun subscriptionHistory(id: UUID, page: PageRequest) =
        executor.protectedCall({ api.subscriptionHistory(it, id, page.page, page.size) }) { it.toPage(page) { item -> item.sandbox() } }

    override suspend fun subscription(id: UUID) =
        executor.protectedCall({ api.subscription(it, id) }) { it.sandbox().verify { it.subscriptionId == id } }

    override suspend fun refreshSubscription(id: UUID) =
        executor.protectedCall({ api.refreshSubscription(it, id) }) { it.sandbox().verify { it.subscriptionId == id } }

    override suspend fun resumeSubscription(id: UUID, body: ResumeSubscription) =
        executor.protectedCall({ api.resumeSubscription(it, id, body) }) { it.sandbox().verify { it.subscriptionId == id } }

    override suspend fun cancelSubscription(id: UUID) =
        executor.protectedCall({ api.cancelSubscription(it, id) }) { it.sandbox().verify { it.subscriptionId == id } }

    override suspend fun compensation(id: UUID) =
        executor.protectedCall({ api.compensation(it, id) }) { it.sandbox().verify { it.collaborationId == id } }

    override suspend fun compensationById(id: UUID) =
        executor.protectedCall({ api.compensationById(it, id) }) { it.sandbox().verify { it.compensationId == id } }

    override suspend fun beginPayment(id: UUID, body: BeginCompensation) =
        executor.protectedCall({ api.beginPayment(it, id, body) }) { it.sandbox().verify { it.collaborationId == id } }

    override suspend fun confirmPayment(id: UUID, body: ConfirmCompensation) =
        executor.protectedCall({ api.confirmPayment(it, id, body) }) { it.sandbox().verify { it.collaborationId == id } }

    override suspend fun refreshPayment(id: UUID) =
        executor.protectedCall({ api.refreshPayment(it, id) }) { it.sandbox().verify { it.collaborationId == id } }

    override suspend fun compensationHistory(id: UUID, page: PageRequest) =
        executor.protectedCall({ api.compensationHistory(it, id, page.page, page.size) }) { it.toPage(page) { row ->
            row.id.required("id")
            row.type.required("type")
            row.time.required("time")
            row
        } }
}

private fun BillingMethod.sandbox(): BillingMethod {
    methodId.required("methodId")
    purpose.required("purpose")
    linkedAt.required("linkedAt")
    status.required("status")
    return verify { provider == "PAYPAL" && environment == "SANDBOX" && version >= 0 }
}
private fun BillingAuthorization.sandbox(): BillingAuthorization {
    authorizationId.required("authorizationId")
    purpose.required("purpose")
    expiresAt.required("expiresAt")
    status.required("status")
    return verify { provider == "PAYPAL" && environment == "SANDBOX" && version >= 0 }
}
private fun BillingSubscription.sandbox(): BillingSubscription {
    status.required("status")
    benefits.required("benefits")
    return verify {
        provider == "PAYPAL" && environment == "SANDBOX" && version >= 0 &&
            ((status == "NONE" && subscriptionId == null && !benefitsActive) ||
                (status != "NONE" && subscriptionId != null && planId != null && (!benefitsActive || paidThrough != null)))
    }
}
private fun BillingCompensation.sandbox(): BillingCompensation {
    compensationId.required("compensationId")
    collaborationId.required("collaborationId")
    agreementId.required("agreementId")
    status.required("status")
    type.required("type")
    availableActions.required("availableActions")
    return verify {
        provider == "PAYPAL" && environment == "SANDBOX" && version >= 0 &&
            (!wasPaid || paidAt != null) && (status != "PAID" || wasPaid) &&
            (!paymentApplicable || amount != null && currency != null)
    }
}
private fun <T> T.verify(predicate: (T) -> Boolean): T {
    if (!predicate(this)) throw InvalidApiResponse("Unexpected billing response")
    return this
}
private fun <D, T> BillingPageDto<D>.toPage(request: PageRequest, map: (D) -> T): Page<T> {
    val rows = items.required("items")
    val count = total.required("total")
    if (page != request.page || size != request.size || count < rows.size || rows.size > request.size)
        throw InvalidApiResponse("Invalid billing page")
    return Page(rows.map(map), count, request.page, request.size)
}
