package com.example.collabpro.foundation

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.di.NetworkModule
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.billing.domain.model.*
import com.example.collabpro.features.billing.infrastructure.remote.*
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit

class BillingEndpointContractsTest {
    private lateinit var server: MockWebServer
    private lateinit var billing: RemoteBilling
    private val sessions = FakeSessions()
    private val gson = ApiJson.create()
    private val account = ApiFixtures.id
    private val resource = UUID.randomUUID()
    private val method = UUID.randomUUID()
    private val plan = UUID.randomUUID()
    private val agreement = UUID.randomUUID()
    private val request = UUID.randomUUID()
    private val now = ApiFixtures.now
    private val approval = "https://www.sandbox.paypal.com/checkoutnow?token=opaque"

    private val activeMethod = BillingMethod(method, BillingPurpose.PAYMENT, "ACTIVE", "PAYPAL", "SANDBOX", "P•••", now, 2)
    private val link = BillingAuthorization(resource, BillingPurpose.PAYMENT, "PAYPAL", "SANDBOX", "PENDING_APPROVAL",
        now.plusSeconds(1200), approval, null, null, 4)
    private val subscription = BillingSubscription(resource, plan, "Esencial", BigDecimal("5.00"), "USD", "APPROVAL_PENDING",
        "APPROVAL_PENDING", "PAYPAL", "SANDBOX", method, false, emptyList(), approval, now.plusSeconds(1200), null, now, null, 5)
    private val compensation = BillingCompensation(resource, account, agreement, "CASH", BigDecimal("5.00"),
        "USD", "Una pieza", "AWAITING_COMPANY_APPROVAL", true, true, 0, false, null, null, null, null,
        "PAYPAL", "SANDBOX", approval, null, null, 7, listOf("CONFIRM_FUNDING"))

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val config = ApiConfiguration(server.url("/api/v1/").toString(), true)
        val retrofit = Retrofit.Builder().baseUrl(config.url)
            .client(NetworkModule.client(config, sessions, ApiFixtures.clock))
            .addConverterFactory(GsonConverterFactory.create(gson)).build()
        billing = RemoteBilling(retrofit.create(BillingApi::class.java), ApiExecutor(gson, sessions, ApiFixtures.clock, config))
    }

    @After fun close() { server.shutdown() }

    @Test fun `method listing validation authorization and cancellation are private`() = runTest {
        response(Page(listOf(activeMethod), 21L, 1, 20))
        assertEquals(activeMethod, billing.methods(account, PageRequest(1)).value().items.single())
        requested("GET", "user/$account/billing?page=1&size=20")

        response(BillingReadiness(method, BillingPurpose.PAYMENT, "PAYPAL", "SANDBOX"))
        assertEquals(method, billing.validateMethod(account, ValidateBillingMethod(method)).value().methodId)
        requested("POST", "user/$account/billing/validation", ValidateBillingMethod(method))

        response(link, 202)
        assertEquals(link, billing.beginLink(account, BeginBilling(request)).value())
        requested("POST", "user/$account/billing", BeginBilling(request))

        response(link.copy(status = "LINKED", methodId = method, approvalUrl = null))
        assertEquals("LINKED", billing.authorization(resource).value().status)
        requested("GET", "billing/authorizations/$resource")

        response(link.copy(status = "CANCELLED", approvalUrl = null))
        assertTrue(billing.cancelLink(resource).value().terminal)
        requested("POST", "billing/authorizations/$resource/cancellation")
    }

    @Test fun `catalog and subscription lifecycle honor receipt and version from server`() = runTest {
        val catalog = SubscriptionPlan(plan, "ESSENTIAL", "Esencial", "Demo", BigDecimal("5.00"), "USD", "MONTH",
            listOf("Seguimiento"), true, true, null)
        response(listOf(catalog))
        assertEquals(catalog, billing.plans().value().single())
        requested("GET", "subscription-plans")
        response(subscription.copy(status = "NONE", subscriptionId = null, planId = null))
        assertTrue(billing.currentSubscription(account).value().canStart)
        requested("GET", "user/$account/subscription")

        val begin = BeginSubscription(request, plan, method)
        response(subscription, 201)
        val pending = billing.beginSubscription(account, begin).value()
        assertFalse(pending.benefitsActive)
        requested("POST", "user/$account/subscription", begin)

        response(Page(listOf(pending), 1L, 0, 20))
        assertEquals(pending, billing.subscriptionHistory(account, PageRequest()).value().items.single())
        requested("GET", "user/$account/subscriptions?page=0&size=20")
        response(pending)
        assertEquals(pending, billing.subscription(resource).value())
        requested("GET", "subscriptions/$resource")
        response(pending)
        assertEquals(pending, billing.resumeSubscription(resource, ResumeSubscription(5)).value())
        requested("POST", "subscriptions/$resource/resume-approval", ResumeSubscription(5))

        val paid = pending.copy(status = "ACTIVE", benefitsActive = true, benefits = catalog.benefits, paidThrough = now.plusSeconds(86400))
        response(paid)
        assertEquals(paid, billing.refreshSubscription(resource).value())
        requested("POST", "subscriptions/$resource/refresh")
        response(paid.copy(benefitsActive = false, benefits = emptyList(), paidThrough = null, status = "CANCEL_PENDING"))
        assertFalse(billing.cancelSubscription(resource).value().benefitsActive)
        requested("POST", "subscriptions/$resource/cancellation")
    }

    @Test fun `compensation separates checkout consent capture payout and history`() = runTest {
        val blocked = compensation.copy(currency = "PEN", approvalUrl = null, availableActions = emptyList(),
            unavailableReason = "UNSUPPORTED_PAYMENT_CURRENCY")
        response(blocked)
        assertEquals("PEN", billing.compensation(account).value().currency)
        requested("GET", "collaborations/$account/compensation")
        response(blocked)
        assertEquals(blocked, billing.compensationById(resource).value())
        requested("GET", "compensations/$resource")

        val begin = BeginCompensation(request, method, 6)
        response(compensation, 201)
        assertEquals(approval, billing.beginPayment(account, begin).value().approvalUrl)
        requested("POST", "collaborations/$account/payment", begin)
        response(compensation)
        assertFalse(billing.refreshPayment(account).value().wasPaid)
        requested("POST", "collaborations/$account/payment/refresh")

        val confirm = ConfirmCompensation(UUID.randomUUID(), 7)
        val funded = compensation.copy(status = "PAYOUT_PENDING", fundedAt = now, version = 8, wasPaid = false,
            availableActions = emptyList(), approvalUrl = null)
        response(funded)
        val result = billing.confirmPayment(account, confirm).value()
        assertEquals(now, result.fundedAt)
        assertNull(result.paidAt)
        assertFalse(result.wasPaid)
        requested("POST", "collaborations/$account/payment/confirmation", confirm)

        val retry = BeginCompensation(UUID.randomUUID(), null, 9)
        response(funded, 201)
        assertEquals(now, billing.beginPayment(account, retry).value().fundedAt)
        val retryRequest = requested("POST", "collaborations/$account/payment", retry)
        assertFalse(retryRequest.has("methodId"))

        val audit = CompensationAudit(UUID.randomUUID(), null, null, "FUNDING_RECONCILED", now)
        response(Page(listOf(audit), 1L, 0, 20))
        assertEquals(audit, billing.compensationHistory(account, PageRequest()).value().items.single())
        requested("GET", "collaborations/$account/compensation/history?page=0&size=20")
    }

    @Test fun `foreign identifiers live provider and contradictory payment are rejected`() = runTest {
        response(link.copy(authorizationId = UUID.randomUUID()))
        assertMalformed(billing.authorization(resource))
        requested("GET", "billing/authorizations/$resource")
        response(link.copy(environment = "LIVE"))
        assertMalformed(billing.authorization(resource))
        requested("GET", "billing/authorizations/$resource")
        response(compensation.copy(status = "PAID", wasPaid = false))
        assertMalformed(billing.compensation(account))
        requested("GET", "collaborations/$account/compensation")
    }

    @Test fun `forbidden never fabricates results and unauthenticated calls stay off the wire`() = runTest {
        response(mapOf("code" to "FORBIDDEN"), 403)
        assertEquals(FailureKind.FORBIDDEN, (billing.compensation(account) as ApiResult.Failure).error.kind)
        requested("GET", "collaborations/$account/compensation")
        sessions.current = null
        assertEquals(FailureKind.UNAUTHORIZED, (billing.plans() as ApiResult.Failure).error.kind)
        assertEquals(1, server.requestCount)
    }

    private fun response(value: Any, status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(gson.toJson(value)))
    }

    private fun requested(verb: String, path: String, payload: Any? = null): com.google.gson.JsonObject {
        val actual = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals(verb, actual.method)
        assertEquals("/api/v1/$path", actual.path)
        assertEquals("Bearer test.jwt.token", actual.getHeader("Authorization"))
        if (payload == null) {
            assertEquals(0L, actual.bodySize)
            return com.google.gson.JsonObject()
        }
        return JsonParser.parseString(actual.body.readUtf8()).asJsonObject.also { assertEquals(gson.toJsonTree(payload), it) }
    }

    private fun <T> ApiResult<T>.value(): T = (this as ApiResult.Success).value
    private fun assertMalformed(result: ApiResult<*>) {
        assertEquals(FailureKind.MALFORMED_RESPONSE, (result as ApiResult.Failure).error.kind)
    }
}
