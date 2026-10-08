package com.example.collabpro.features.identity.infrastructure.remote

import com.example.collabpro.core.application.security.SessionCredentials
import retrofit2.Response
import retrofit2.http.*

internal interface IdentityApi {
    @POST("auth/brands") suspend fun registerBrand(@Body body: BrandRegistrationDto): Response<AccountDto>
    @POST("auth/creators") suspend fun registerCreator(@Body body: CreatorRegistrationDto): Response<AccountDto>
    @POST("auth/sessions") suspend fun signIn(@Body body: LoginDto): Response<SessionDto>
    @POST("auth/recovery-requests") suspend fun recover(@Body body: RecoveryDto): Response<RecoveryAcceptedDto>
    @POST("auth/password-resets") suspend fun reset(@Body body: PasswordResetDto): Response<Unit>
    @GET("accounts/me") suspend fun account(@Tag session: SessionCredentials): Response<AccountDto>
    @GET("profiles/me/creator") suspend fun profile(@Tag session: SessionCredentials): Response<CreatorProfileDto>
    @PUT("profiles/me/creator") suspend fun updateProfile(@Tag session: SessionCredentials, @Body body: ProfileUpdateDto): Response<CreatorProfileDto>
    @POST("social-accounts/{platform}/authorizations") suspend fun authorize(
        @Tag session: SessionCredentials, @Path("platform") platform: String, @Query("client") client: String = "ANDROID"
    ): Response<SocialAuthorizationDto>
    @GET("social-accounts/me") suspend fun socialAccounts(@Tag session: SessionCredentials): Response<List<SocialAccountDto>>
    @GET("social-accounts/authorizations/{id}") suspend fun authorizationStatus(
        @Tag session: SessionCredentials, @Path("id") id: String
    ): Response<AuthorizationAttemptDto>
}
