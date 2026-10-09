package com.example.collabpro.features.identity.presentation.profile

import com.example.collabpro.core.domain.ApiFailure
import com.example.collabpro.features.identity.domain.model.*
import java.net.URI
import java.util.UUID
import java.time.Instant

data class ProfileDraft(
    val displayName: String = "", val biography: String = "", val niche: String = "",
    val audienceDescription: String = "", val location: String = ""
) {
    fun request() = CreatorProfileUpdate(displayName.trim(), biography.trim().ifBlank { null },
        niche.trim().ifBlank { null }, audienceDescription.trim().ifBlank { null }, location.trim().ifBlank { null })
    companion object {
        fun from(profile: CreatorProfile) = ProfileDraft(profile.displayName, profile.biography.orEmpty(),
            profile.niche.orEmpty(), profile.audienceDescription.orEmpty(), profile.location.orEmpty())
    }
}

data class CreatorProfileUiState(
    val profile: CreatorProfile? = null, val draft: ProfileDraft = ProfileDraft(),
    val loading: Boolean = false, val saving: Boolean = false,
    val failure: ApiFailure? = null, val notice: String? = null, val accountRefreshFailed: Boolean = false
) {
    val dirty: Boolean get() = profile != null && draft != ProfileDraft.from(profile)
}

data class SocialLinkUiState(
    val authorizationId: UUID, val platform: SocialPlatform?, val status: AuthorizationStatus = AuthorizationStatus.PENDING,
    val checking: Boolean = false, val failure: ApiFailure? = null, val message: String? = null
)

data class SocialAccountsUiState(
    val accounts: List<SocialAccount> = emptyList(), val loaded: Boolean = false,
    val loading: Boolean = false, val starting: SocialPlatform? = null,
    val failure: ApiFailure? = null, val link: SocialLinkUiState? = null
)

data class CreatorIdentityUiState(
    val profile: CreatorProfileUiState = CreatorProfileUiState(),
    val social: SocialAccountsUiState = SocialAccountsUiState(),
    val showSocial: Boolean = false, val awaitingLogin: Boolean = false, val externalNotice: String? = null,
    val ownerId: UUID? = null, val expiresAt: Instant? = null
)

/** Transient browser request; OAuth URL (including provider state) is never saved or logged. */
class OpenSocialBrowser(val owner: UUID, val authorizationId: UUID, val platform: SocialPlatform, val uri: URI, val expiresAt: Instant) {
    override fun toString() = "OpenSocialBrowser(owner=$owner, authorizationId=$authorizationId, url=<redacted>)"
}
