package com.example.collabpro.features.identity.infrastructure.remote

import java.time.Instant

internal data class BrandRegistrationDto(val businessName: String, val email: String, val password: String) {
    override fun toString() = "BrandRegistrationDto(<redacted>)"
}
internal data class CreatorRegistrationDto(val displayName: String, val email: String, val password: String) {
    override fun toString() = "CreatorRegistrationDto(<redacted>)"
}
internal data class LoginDto(val email: String, val password: String) {
    override fun toString() = "LoginDto(<redacted>)"
}
internal data class RecoveryDto(val email: String)
internal data class PasswordResetDto(val token: String, val newPassword: String) {
    override fun toString() = "PasswordResetDto(<redacted>)"
}
internal data class ProfileUpdateDto(val displayName: String, val biography: String?, val niche: String?, val audienceDescription: String?, val location: String?)
internal data class AccountDto(val accountId: String?, val profileId: String?, val name: String?, val accountType: String?, val status: String?)
internal data class SessionDto(val account: AccountDto?, val accessToken: String?, val tokenType: String?, val expiresAt: Instant?) {
    override fun toString() = "SessionDto(<redacted>)"
}
internal data class RecoveryAcceptedDto(val message: String?)
internal data class CreatorProfileDto(val profileId: String?, val displayName: String?, val biography: String?, val niche: String?, val audienceDescription: String?, val location: String?)
internal data class SocialAccountDto(val id: String?, val platform: String?, val username: String?, val status: String?)
internal data class SocialAuthorizationDto(val authorizationUrl: String?, val authorizationId: String?)
internal data class AuthorizationAttemptDto(val authorizationId: String?, val platform: String?, val status: String?, val errorCode: String?, val expiresAt: Instant?)
