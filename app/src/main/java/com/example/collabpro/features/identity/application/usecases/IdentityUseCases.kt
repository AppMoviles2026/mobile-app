package com.example.collabpro.features.identity.application.usecases

import com.example.collabpro.features.identity.domain.model.*
import com.example.collabpro.features.identity.domain.repositories.IdentityRepository
import com.example.collabpro.features.identity.domain.repositories.SessionStore
import com.example.collabpro.features.identity.application.profile.CreatorProfileValidation
import com.example.collabpro.core.domain.ApiResult
import java.util.UUID

/** Public authentication calls never silently log out or overwrite an existing session. */
class RegisterBrand(private val repository: IdentityRepository) {
    suspend operator fun invoke(businessName: String, email: String, password: String) = repository.registerBrand(businessName, email, password)
}
class RegisterCreator(private val repository: IdentityRepository) {
    suspend operator fun invoke(displayName: String, email: String, password: String) = repository.registerCreator(displayName, email, password)
}
class SignIn(private val repository: IdentityRepository) {
    suspend operator fun invoke(email: String, password: String) = repository.signIn(email, password)
}
class RequestPasswordRecovery(private val repository: IdentityRepository) {
    suspend operator fun invoke(email: String) = repository.requestRecovery(email)
}
class ResetPassword(private val repository: IdentityRepository) {
    suspend operator fun invoke(token: String, newPassword: String) = repository.resetPassword(token, newPassword)
}
class GetCurrentAccount(private val repository: IdentityRepository) {
    suspend operator fun invoke() = repository.currentAccount()
}
class GetCreatorProfile(private val repository: IdentityRepository) {
    suspend operator fun invoke() = repository.creatorProfile()
}
class UpdateCreatorProfile(private val repository: IdentityRepository) {
    suspend operator fun invoke(update: CreatorProfileUpdate): ApiResult<CreatorProfile> {
        val failure = CreatorProfileValidation.validate(update)
        return if (failure == null) repository.updateCreatorProfile(update) else ApiResult.Failure(failure)
    }
}
class StartSocialAuthorization(private val repository: IdentityRepository) {
    suspend operator fun invoke(platform: SocialPlatform) = repository.authorizeSocialAccount(platform)
}
class GetSocialAccounts(private val repository: IdentityRepository) {
    suspend operator fun invoke() = repository.socialAccounts()
}
class GetAuthorizationStatus(private val repository: IdentityRepository) {
    suspend operator fun invoke(id: UUID) = repository.authorizationStatus(id)
}
class ReadStoredSession(private val store: SessionStore) {
    suspend operator fun invoke() = store.read()
}
class StoreSession(private val store: SessionStore) {
    suspend operator fun invoke(session: Session) = store.save(session)
}
class ClearLocalSession(private val store: SessionStore) {
    suspend operator fun invoke() = store.clear()
}

class IdentityUseCases(repository: IdentityRepository, store: SessionStore) {
    val registerBrand = RegisterBrand(repository)
    val registerCreator = RegisterCreator(repository)
    val signIn = SignIn(repository)
    val requestPasswordRecovery = RequestPasswordRecovery(repository)
    val resetPassword = ResetPassword(repository)
    val getCurrentAccount = GetCurrentAccount(repository)
    val getCreatorProfile = GetCreatorProfile(repository)
    val updateCreatorProfile = UpdateCreatorProfile(repository)
    val startSocialAuthorization = StartSocialAuthorization(repository)
    val getSocialAccounts = GetSocialAccounts(repository)
    val getAuthorizationStatus = GetAuthorizationStatus(repository)
    val readStoredSession = ReadStoredSession(store)
    val storeSession = StoreSession(store)
    val clearLocalSession = ClearLocalSession(store)
}
