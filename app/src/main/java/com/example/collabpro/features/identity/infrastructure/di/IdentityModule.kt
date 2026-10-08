package com.example.collabpro.features.identity.infrastructure.di

import android.content.Context
import com.example.collabpro.core.application.security.SessionAccess
import com.example.collabpro.core.infrastructure.network.ApiExecutor
import com.example.collabpro.features.identity.application.usecases.IdentityUseCases
import com.example.collabpro.features.identity.application.auth.AuthenticationSession
import com.example.collabpro.features.identity.domain.repositories.*
import com.example.collabpro.features.identity.infrastructure.RemoteIdentityRepository
import com.example.collabpro.features.identity.infrastructure.remote.IdentityApi
import com.example.collabpro.features.identity.infrastructure.session.*
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object IdentityModule {
    @Provides @Singleton fun sessions(@ApplicationContext context: Context, gson: Gson, clock: Clock): EncryptedSessionStore {
        val key = AndroidSessionKey()
        return EncryptedSessionStore(AndroidSessionStorage(context), SessionCipher(key::get), gson, clock)
    }
    @Provides fun sessionStore(store: EncryptedSessionStore): SessionStore = store
    @Provides fun sessionAccess(store: EncryptedSessionStore): SessionAccess = store
    @Provides @Singleton fun api(retrofit: Retrofit): IdentityApi = retrofit.create(IdentityApi::class.java)
    @Provides @Singleton fun repository(api: IdentityApi, executor: ApiExecutor): IdentityRepository = RemoteIdentityRepository(api, executor)
    @Provides @Singleton fun useCases(repository: IdentityRepository, store: SessionStore) = IdentityUseCases(repository, store)
    @Provides fun authentication(repository: IdentityRepository, store: SessionStore, clock: Clock) = AuthenticationSession(repository, store, clock)
}
