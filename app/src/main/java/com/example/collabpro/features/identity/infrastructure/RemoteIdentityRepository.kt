package com.example.collabpro.features.identity.infrastructure

import com.example.collabpro.core.infrastructure.network.ApiExecutor
import com.example.collabpro.core.infrastructure.network.required
import com.example.collabpro.features.identity.domain.model.*
import com.example.collabpro.features.identity.domain.repositories.IdentityRepository
import com.example.collabpro.features.identity.infrastructure.remote.*
import java.util.UUID

internal class RemoteIdentityRepository(private val api: IdentityApi, private val executor: ApiExecutor) : IdentityRepository {
    override suspend fun registerBrand(businessName: String, email: String, password: String) =
        executor.publicCall({ api.registerBrand(BrandRegistrationDto(businessName, email, password)) }) { it.toDomain() }
    override suspend fun registerCreator(displayName: String, email: String, password: String) =
        executor.publicCall({ api.registerCreator(CreatorRegistrationDto(displayName, email, password)) }) { it.toDomain() }
    override suspend fun signIn(email: String, password: String) =
        executor.publicCall({ api.signIn(LoginDto(email, password)) }) { it.toDomain() }
    override suspend fun requestRecovery(email: String) =
        executor.publicCall({ api.recover(RecoveryDto(email)) }) { it.message.required("message") }
    override suspend fun resetPassword(token: String, newPassword: String) =
        executor.publicUnit { api.reset(PasswordResetDto(token, newPassword)) }
    override suspend fun currentAccount() = executor.protectedCall({ api.account(it) }) { it.toDomain() }
    override suspend fun creatorProfile() = executor.protectedCall({ api.profile(it) }) { it.toDomain() }
    override suspend fun updateCreatorProfile(update: CreatorProfileUpdate) = executor.protectedCall({ session ->
        api.updateProfile(session, ProfileUpdateDto(update.displayName, update.biography, update.niche, update.audienceDescription, update.location))
    }) { it.toDomain() }
    override suspend fun authorizeSocialAccount(platform: SocialPlatform) =
        executor.protectedCall({ api.authorize(it, platform.wireValue) }) { it.toDomain() }
    override suspend fun socialAccounts() = executor.protectedCall({ api.socialAccounts(it) }) { items -> items.map { it.toDomain() } }
    override suspend fun authorizationStatus(id: UUID) = executor.protectedCall({ api.authorizationStatus(it, id.toString()) }) { it.toDomain() }
}
