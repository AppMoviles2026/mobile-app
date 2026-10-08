package com.example.collabpro.core.infrastructure.di

import com.example.collabpro.BuildConfig
import com.example.collabpro.core.application.security.SessionAccess
import com.example.collabpro.core.infrastructure.network.*
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun clock(): Clock = Clock.systemUTC()
    @Provides @Singleton fun gson(): Gson = ApiJson.create()
    @Provides @Singleton fun configuration() = ApiConfiguration(BuildConfig.API_BASE_URL, BuildConfig.DEBUG)
    @Provides @Singleton fun client(configuration: ApiConfiguration, sessions: SessionAccess, clock: Clock): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(BearerInterceptor(configuration, sessions, clock))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            // Avoid token leaks on redirects and uncertain writes being retried without the user's intent key.
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .build()
    @Provides @Singleton fun retrofit(configuration: ApiConfiguration, client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder().baseUrl(configuration.url).client(client).addConverterFactory(GsonConverterFactory.create(gson)).build()
    @Provides @Singleton fun executor(gson: Gson, sessions: SessionAccess, clock: Clock, configuration: ApiConfiguration) =
        ApiExecutor(gson, sessions, clock, configuration)
}
