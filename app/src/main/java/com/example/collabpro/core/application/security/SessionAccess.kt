package com.example.collabpro.core.application.security

import java.time.Instant
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Non-secret initiating actor: a queued workflow must not acquire another user's credentials on IO. */
class ExpectedAccount(val accountId: UUID, val expiresAt: Instant) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ExpectedAccount>
}

/** Snapshot ties each protected request to the session that initiated it. */
data class SessionCredentials(
    val accountId: UUID,
    val accessToken: String,
    val expiresAt: Instant,
    val revision: Long
) {
    override fun toString() = "SessionCredentials(accountId=$accountId, revision=$revision, token=<redacted>)"
}

interface SessionAccess {
    fun credentials(): SessionCredentials?
    fun isCurrent(credentials: SessionCredentials): Boolean
    fun invalidateIfCurrent(credentials: SessionCredentials)
}
