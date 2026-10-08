package com.example.collabpro.core.infrastructure.network

import com.example.collabpro.core.application.security.SessionAccess
import com.example.collabpro.core.application.security.SessionCredentials
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.time.Clock

internal class SessionChangedException : IOException("Session changed before request was sent")
internal class UntrustedRequestException : IOException("Refused an untrusted API request")

/** Only trusted protected requests tagged with their original session receive a token. */
class BearerInterceptor(
    private val configuration: ApiConfiguration,
    private val sessions: SessionAccess,
    private val clock: Clock
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!configuration.trusts(request.url)) throw UntrustedRequestException()
        val builder = request.newBuilder().removeHeader("Authorization")
        if (configuration.isProtected(request.url)) {
            val credentials = request.tag(SessionCredentials::class.java) ?: throw SessionChangedException()
            if (!sessions.isCurrent(credentials) || !credentials.expiresAt.isAfter(clock.instant())) throw SessionChangedException()
            builder.header("Authorization", "Bearer ${credentials.accessToken}")
        }
        return chain.proceed(builder.build())
    }
}
