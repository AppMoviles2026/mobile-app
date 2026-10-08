package com.example.collabpro.features.identity.domain.model

import java.net.URI
import java.time.Instant
import java.util.UUID

enum class AccountType { BRAND, CREATOR }
enum class AccountStatus { ACTIVE, PENDING, SUSPENDED, DISABLED }
data class Account(val accountId: UUID, val profileId: UUID, val name: String, val accountType: AccountType, val status: AccountStatus)

data class Session(val account: Account, val accessToken: String, val expiresAt: Instant) {
    override fun toString() = "Session(account=$account, expiresAt=$expiresAt, token=<redacted>)"
}

data class CreatorProfile(
    val profileId: UUID, val displayName: String, val biography: String?,
    val niche: String?, val audienceDescription: String?, val location: String?
)

enum class SocialPlatform(val wireValue: String) { INSTAGRAM("instagram"), TIKTOK("tiktok") }
enum class SocialAccountStatus { ACTIVE, REVOKED }
data class SocialAccount(val id: UUID, val platform: SocialPlatform, val username: String, val status: SocialAccountStatus)
data class SocialAuthorization(val authorizationUrl: URI, val authorizationId: UUID) {
    override fun toString() = "SocialAuthorization(authorizationId=$authorizationId, url=<redacted>)"
}
enum class AuthorizationStatus { PENDING, SUCCEEDED, FAILED, EXPIRED }
data class AuthorizationAttempt(
    val authorizationId: UUID, val platform: SocialPlatform, val status: AuthorizationStatus,
    val errorCode: String?, val expiresAt: Instant
)
data class CreatorProfileUpdate(
    val displayName: String, val biography: String? = null, val niche: String? = null,
    val audienceDescription: String? = null, val location: String? = null
)
