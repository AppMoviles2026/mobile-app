package com.example.collabpro.features.identity.presentation.profile

import com.example.collabpro.core.domain.ApiFailure
import com.example.collabpro.features.identity.domain.model.BusinessProfile
import com.example.collabpro.features.identity.domain.model.BusinessProfileUpdate
import java.time.Instant
import java.util.UUID

internal data class BrandProfileSession(val accountId: UUID, val profileId: UUID, val expiresAt: Instant)

data class BrandProfileDraft(
    val businessName: String,
    val description: String = "",
    val category: String = "",
    val location: String = ""
) {
    fun update(version: Long) =
        BusinessProfileUpdate(businessName, description, category, location, version).normalized()

    // Applying a draft to a newer version should keep server edits to fields the user never touched.
    internal fun reapply(original: BusinessProfile, latest: BusinessProfile): BrandProfileDraft {
        val change = update(original.version)
        return from(latest).copy(
            businessName = if (change.businessName != original.businessName) businessName else latest.businessName,
            description = if (change.description != original.description) description else latest.description.orEmpty(),
            category = if (change.category != original.category) category else latest.category.orEmpty(),
            location = if (change.location != original.location) location else latest.location.orEmpty()
        )
    }

    companion object {
        fun from(profile: BusinessProfile) = BrandProfileDraft(
            profile.businessName, profile.description.orEmpty(), profile.category.orEmpty(), profile.location.orEmpty()
        )
    }
}

data class BrandProfileUiState(
    val profile: BusinessProfile? = null,
    val draft: BrandProfileDraft? = null,
    val latestForReview: BusinessProfile? = null,
    val needsReload: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val refreshingAccount: Boolean = false,
    val failure: ApiFailure? = null,
    val accountRefreshFailure: ApiFailure? = null,
    val notice: String? = null
) {
    val busy: Boolean get() = loading || saving || refreshingAccount
    val dirty: Boolean get() = profile != null && draft != null &&
        draft.update(profile.version) != BrandProfileDraft.from(profile).update(profile.version)
    val canSave: Boolean get() = dirty && !busy && !needsReload && latestForReview == null
}
