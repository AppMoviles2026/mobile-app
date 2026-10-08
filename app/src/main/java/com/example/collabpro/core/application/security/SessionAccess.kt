package com.example.collabpro.core.application.security

import java.time.Instant
import java.util.UUID

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
