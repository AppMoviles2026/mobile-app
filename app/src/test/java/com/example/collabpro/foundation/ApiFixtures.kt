package com.example.collabpro.foundation

import com.example.collabpro.core.application.security.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal object ApiFixtures {
    const val ID = "00000000-0000-0000-0000-000000000001"
    const val PROFILE_ID = "00000000-0000-0000-0000-000000000002"
    const val REQUIREMENT_ID = "00000000-0000-0000-0000-000000000003"
    val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
    val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    val id: UUID = UUID.fromString(ID)
    val account = """{"accountId":"$ID","profileId":"$PROFILE_ID","name":"Marca","accountType":"BRAND","status":"ACTIVE"}"""
    val session = """{"account":$account,"accessToken":"test.jwt.token","tokenType":"Bearer","expiresAt":"2030-01-01T01:00:00Z"}"""
    val profile = """{"profileId":"$PROFILE_ID","displayName":"Ana","biography":null,"niche":null,"audienceDescription":null,"location":null}"""
    val social = """{"id":"$ID","platform":"instagram","username":"ana","status":"ACTIVE"}"""
    val authorization = """{"authorizationUrl":"https://provider.example/authorize?state=opaque","authorizationId":"$ID"}"""
    val attempt = """{"authorizationId":"$ID","platform":"instagram","status":"SUCCEEDED","errorCode":null,"expiresAt":"2030-01-01T01:00:00Z"}"""
    val draft = """{"id":"$ID","brandId":"$PROFILE_ID","brandName":"Marca","title":"Campaña","category":"Belleza","location":null,"compensation":null,"applicationDeadline":null,"status":"DRAFT","acceptsApplications":false,"objective":"Promoción","description":null,"targetAudience":"Jóvenes","publicationDate":null,"requirements":[],"deliverables":[]}"""
    val campaign = """{"id":"$ID","brandId":"$PROFILE_ID","brandName":"Marca","title":"Campaña","category":"Belleza","location":"Lima","compensation":{"type":"CASH","amount":9999999999.99,"currency":"PEN","description":"Pago por contenido"},"applicationDeadline":"2030-02-01T00:00:00Z","status":"OPEN","acceptsApplications":true,"objective":"Promoción","description":null,"targetAudience":"Jóvenes","publicationDate":"2030-01-01T00:00:00Z","requirements":[{"id":"$REQUIREMENT_ID","description":"Acepta requisitos","mandatory":true,"ruleType":"MANUAL_CONFIRMATION","expectedValue":null}],"deliverables":[{"id":"$PROFILE_ID","contentType":"Reel","description":"Demostración","quantity":1,"deadline":"2030-03-01T00:00:00Z"}]}"""
    val application = """{"id":"$ID","campaignId":"$PROFILE_ID","creatorId":"$REQUIREMENT_ID","campaignTitle":"Campaña","brandName":"Marca","message":"Mi propuesta","status":"PENDING","submittedAt":"2030-01-01T00:00:00Z","confirmedRequirementIds":["$REQUIREMENT_ID"],"version":0}"""
    fun page(item: String) = """{"items":[$item],"total":1,"page":0,"size":20}"""
}

internal class FakeSessions : SessionAccess {
    @Volatile var current: SessionCredentials? = SessionCredentials(ApiFixtures.id, "test.jwt.token", ApiFixtures.now.plusSeconds(3600), 1)
    var invalidations = 0
    override fun credentials() = current
    override fun isCurrent(credentials: SessionCredentials) = current == credentials
    override fun invalidateIfCurrent(credentials: SessionCredentials) {
        if (isCurrent(credentials)) { current = null; invalidations++ }
    }
    fun switchAccount() { current = SessionCredentials(UUID.randomUUID(), "another.jwt.token", ApiFixtures.now.plusSeconds(3600), 2) }
}
