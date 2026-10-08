package com.example.collabpro.features.identity.domain.repositories

import com.example.collabpro.core.domain.ApiResult
import com.example.collabpro.features.identity.domain.model.*
import java.util.UUID

interface IdentityRepository {
    suspend fun registerBrand(businessName: String, email: String, password: String): ApiResult<Account>
    suspend fun registerCreator(displayName: String, email: String, password: String): ApiResult<Account>
    suspend fun signIn(email: String, password: String): ApiResult<Session>
    suspend fun requestRecovery(email: String): ApiResult<String>
    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit>
    suspend fun currentAccount(): ApiResult<Account>
    suspend fun creatorProfile(): ApiResult<CreatorProfile>
    suspend fun updateCreatorProfile(update: CreatorProfileUpdate): ApiResult<CreatorProfile>
    suspend fun authorizeSocialAccount(platform: SocialPlatform): ApiResult<SocialAuthorization>
    suspend fun socialAccounts(): ApiResult<List<SocialAccount>>
    suspend fun authorizationStatus(id: UUID): ApiResult<AuthorizationAttempt>
}

/** Stored session is a candidate; GET accounts/me must verify it before resuming a user flow. */
interface SessionStore {
    suspend fun read(): Session?
    suspend fun save(session: Session): ApiResult<Unit>
    suspend fun clear(): ApiResult<Unit>
}
