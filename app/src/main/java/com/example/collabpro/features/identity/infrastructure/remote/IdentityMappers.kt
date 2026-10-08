package com.example.collabpro.features.identity.infrastructure.remote

import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.identity.domain.model.*
import java.net.URI

internal fun AccountDto.toDomain() = Account(accountId.uuid("accountId"), profileId.uuid("profileId"),
    name.required("name"), accountType.enum("accountType"), status.enum("status"))
internal fun SessionDto.toDomain(): Session {
    if (tokenType != "Bearer") throw InvalidApiResponse("Unsupported token type")
    val token = accessToken.required("accessToken")
    if (token.isBlank() || token.any { it.isWhitespace() || it.isISOControl() }) throw InvalidApiResponse("Invalid token")
    return Session(account.required("account").toDomain(), token, expiresAt.required("expiresAt"))
}
internal fun CreatorProfileDto.toDomain() = CreatorProfile(profileId.uuid("profileId"), displayName.required("displayName"),
    biography, niche, audienceDescription, location)
internal fun String?.platform(): SocialPlatform = SocialPlatform.entries.firstOrNull { it.wireValue == this }
    ?: throw InvalidApiResponse("Unknown social platform")
internal fun SocialAccountDto.toDomain() = SocialAccount(id.uuid("id"), platform.platform(), username.required("username"), status.enum("status"))
internal fun SocialAuthorizationDto.toDomain(): SocialAuthorization {
    val uri = URI.create(authorizationUrl.required("authorizationUrl"))
    if (uri.scheme != "https" || uri.host == null || uri.userInfo != null) throw InvalidApiResponse("Invalid authorization URL")
    return SocialAuthorization(uri, authorizationId.uuid("authorizationId"))
}
internal fun AuthorizationAttemptDto.toDomain() = AuthorizationAttempt(authorizationId.uuid("authorizationId"), platform.platform(),
    status.enum("status"), errorCode, expiresAt.required("expiresAt"))
