package com.example.collabpro.features.identity.application.auth

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.identity.domain.model.*
import com.example.collabpro.features.identity.domain.repositories.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

sealed interface SessionState {
    data object Restoring : SessionState
    data class SignedOut(val notice: String? = null) : SessionState
    data class Authenticated(val account: Account, val expiresAt: Instant) : SessionState
    data class VerificationFailed(val failure: ApiFailure) : SessionState
}

/** Authentication workflow; neither Android navigation nor a locally chosen role is authoritative. */
class AuthenticationSession(
    private val repository: IdentityRepository,
    private val store: SessionStore,
    private val clock: Clock
) {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private val generation = AtomicLong()
    private var verifiedToken: String? = null

    fun cancelPendingAuthentication() { generation.incrementAndGet() }

    /** Refresh server-owned metadata after a profile change without replacing the private navigation. */
    suspend fun refreshAccount(): ApiResult<Unit> {
        val expected = state.value as? SessionState.Authenticated ?: return changedSession()
        val attempt = generation.get()
        val result = repository.currentAccount()
        return mutex.withLock {
            if (attempt != generation.get() || state.value != expected) return@withLock changedSession()
            val candidate = store.read() ?: return@withLock changedSession()
            if (candidate.accessToken != verifiedToken) return@withLock changedSession()
            when (result) {
                is ApiResult.Failure -> result
                is ApiResult.Success -> {
                    val account = result.value
                    if (account.accountId != expected.account.accountId || account.profileId != expected.account.profileId ||
                        account.accountType != expected.account.accountType || account.status != AccountStatus.ACTIVE) {
                        return@withLock ApiResult.Failure(ApiFailure(FailureKind.MALFORMED_RESPONSE,
                            message = "El servidor devolvió una cuenta diferente a la sesión verificada."))
                    }
                    if (!candidate.expiresAt.isAfter(clock.instant())) return@withLock changedSession()
                    when (val saved = store.save(candidate.copy(account = account))) {
                        is ApiResult.Failure -> saved
                        is ApiResult.Success -> {
                            mutableState.value = expected.copy(account = account)
                            ApiResult.Success(Unit)
                        }
                    }
                }
            }
        }
    }

    suspend fun restore() {
        val attempt = generation.incrementAndGet()
        mutableState.value = SessionState.Restoring
        val candidate = store.read()
        if (candidate == null) {
            mutex.withLock { if (attempt == generation.get()) mutableState.value = SessionState.SignedOut() }
            return
        }
        if (!candidate.expiresAt.isAfter(clock.instant())) {
            endSession(attempt, "Tu sesión venció. Inicia sesión de nuevo.")
            return
        }
        val result = repository.currentAccount()
        mutex.withLock {
            if (attempt != generation.get()) return@withLock
            val current = store.read()
            if (current?.accessToken != candidate.accessToken) {
                verifiedToken = null
                mutableState.value = SessionState.SignedOut("Inicia sesión para continuar.")
                return@withLock
            }
            when (result) {
                is ApiResult.Success -> {
                    val account = result.value
                    if (account.accountId != candidate.account.accountId || account.status != AccountStatus.ACTIVE) {
                        store.clear()
                        verifiedToken = null
                        mutableState.value = SessionState.SignedOut("Esta sesión ya no permite acceder a la cuenta.")
                    } else if (!candidate.expiresAt.isAfter(clock.instant())) {
                        store.clear()
                        mutableState.value = SessionState.SignedOut("Tu sesión venció. Inicia sesión de nuevo.")
                    } else {
                        when (val saved = store.save(candidate.copy(account = account))) {
                            is ApiResult.Success -> {
                                verifiedToken = candidate.accessToken
                                mutableState.value = SessionState.Authenticated(account, candidate.expiresAt)
                            }
                            is ApiResult.Failure -> mutableState.value = SessionState.VerificationFailed(saved.error)
                        }
                    }
                }
                is ApiResult.Failure -> {
                    if (result.error.kind == FailureKind.UNAUTHORIZED || result.error.kind == FailureKind.FORBIDDEN) {
                        store.clear()
                        verifiedToken = null
                        mutableState.value = SessionState.SignedOut("La sesión dejó de ser válida. Inicia sesión de nuevo.")
                    } else mutableState.value = SessionState.VerificationFailed(result.error)
                }
            }
        }
    }

    suspend fun signIn(email: String, password: String): ApiResult<Account> {
        val attempt = generation.incrementAndGet()
        val response = repository.signIn(email, password)
        if (response is ApiResult.Failure) return response
        val session = (response as ApiResult.Success).value
        if (session.account.status != AccountStatus.ACTIVE || !session.expiresAt.isAfter(clock.instant())) {
            return ApiResult.Failure(ApiFailure(FailureKind.UNAUTHORIZED, message = "La cuenta no tiene una sesión activa válida."))
        }
        // Complete a local commit atomically, even if screen navigation cancels the initiating coroutine.
        return withContext(NonCancellable) { mutex.withLock {
            if (attempt != generation.get()) return@withLock changedSession()
            when (val saved = store.save(session)) {
                is ApiResult.Failure -> saved
                is ApiResult.Success -> {
                    if (attempt != generation.get()) {
                        store.clear()
                        changedSession()
                    } else {
                        verifiedToken = session.accessToken
                        mutableState.value = SessionState.Authenticated(session.account, session.expiresAt)
                        ApiResult.Success(session.account)
                    }
                }
            }
        } }
    }

    suspend fun signOut(notice: String? = null): ApiResult<Unit> {
        val attempt = generation.incrementAndGet()
        return withContext(NonCancellable) { mutex.withLock {
            if (attempt != generation.get()) return@withLock changedSession()
            verifiedToken = null
            mutableState.value = SessionState.SignedOut(notice)
            val result = store.clear()
            if (result is ApiResult.Failure) mutableState.value = SessionState.SignedOut(result.error.message)
            result
        } }
    }

    suspend fun expire(expected: SessionState.Authenticated) {
        mutex.withLock {
            if (mutableState.value == expected && !expected.expiresAt.isAfter(clock.instant())) {
                generation.incrementAndGet()
                verifiedToken = null
                mutableState.value = SessionState.SignedOut("Tu sesión venció. Inicia sesión de nuevo.")
                store.clear()
            }
        }
    }

    /** Protected 401 from any bounded context must remove access and the user's back stack. */
    suspend fun observeInvalidations() {
        store.changes.collect {
            mutex.withLock {
                val state = mutableState.value
                if (state is SessionState.Authenticated || state is SessionState.VerificationFailed) {
                    val current = store.read()
                    if (current == null || (state is SessionState.Authenticated && current.accessToken != verifiedToken)) {
                        generation.incrementAndGet()
                        verifiedToken = null
                        mutableState.value = SessionState.SignedOut("La sesión terminó. Inicia sesión de nuevo.")
                    }
                }
            }
        }
    }

    private suspend fun endSession(attempt: Long, notice: String) = mutex.withLock {
        if (attempt == generation.get()) {
            store.clear()
            verifiedToken = null
            mutableState.value = SessionState.SignedOut(notice)
        }
    }
    private fun changedSession() = ApiResult.Failure(ApiFailure(FailureKind.SESSION_CHANGED, message = "La solicitud anterior fue descartada."))
}
