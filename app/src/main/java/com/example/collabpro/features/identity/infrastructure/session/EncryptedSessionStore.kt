package com.example.collabpro.features.identity.infrastructure.session

import com.example.collabpro.core.application.security.*
import com.example.collabpro.core.domain.*
import com.example.collabpro.features.identity.domain.model.Session
import com.example.collabpro.features.identity.domain.repositories.SessionStore
import com.example.collabpro.features.identity.infrastructure.remote.SessionDto
import com.example.collabpro.features.identity.infrastructure.remote.toDomain
import com.example.collabpro.features.identity.infrastructure.remote.AccountDto
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock

/** The JWT is opaque. Account type comes from the backend response, not a local JWT decoder. */
internal class EncryptedSessionStore(
    private val storage: EncryptedSessionStorage,
    private val cipher: SessionCipher,
    private val gson: Gson,
    private val clock: Clock,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : SessionStore, SessionAccess {
    private var loaded = false
    private var session: Session? = null
    private var revision = 0L
    private val lock = Any()

    override suspend fun read(): Session? = withContext(io) { synchronized(lock) { load(); session } }
    override suspend fun save(session: Session): ApiResult<Unit> = withContext(io) {
        synchronized(lock) {
            if (!session.expiresAt.isAfter(clock.instant()) || session.accessToken.isBlank()) {
                return@synchronized ApiResult.Failure(ApiFailure(FailureKind.UNAUTHORIZED, message = "La sesión ya no es válida."))
            }
            try {
                val account = session.account
                val dto = SessionDto(AccountDto(account.accountId.toString(), account.profileId.toString(), account.name,
                    account.accountType.name, account.status.name), session.accessToken, "Bearer", session.expiresAt)
                // Run the same strict validation as a network session, before persisting.
                dto.toDomain()
                val plain = gson.toJson(dto).toByteArray(Charsets.UTF_8)
                val encrypted = try { cipher.encrypt(plain) } finally { plain.fill(0) }
                if (!storage.write(encrypted)) return@synchronized storageFailure()
                this@EncryptedSessionStore.session = session
                loaded = true
                revision++
                ApiResult.Success(Unit)
            } catch (_: Exception) { storageFailure() }
        }
    }

    override suspend fun clear(): ApiResult<Unit> = withContext(io) { synchronized(lock) {
        session = null
        loaded = true
        revision++
        try { if (storage.clear()) ApiResult.Success(Unit) else storageFailure() }
        catch (_: Exception) { storageFailure() }
    } }

    override fun credentials(): SessionCredentials? = synchronized(lock) {
        load()
        session?.let { SessionCredentials(it.account.accountId, it.accessToken, it.expiresAt, revision) }
    }
    override fun isCurrent(credentials: SessionCredentials): Boolean = synchronized(lock) {
        load()
        revision == credentials.revision && session?.account?.accountId == credentials.accountId &&
            session?.accessToken == credentials.accessToken
    }
    override fun invalidateIfCurrent(credentials: SessionCredentials) = synchronized(lock) {
        if (isCurrent(credentials)) {
            session = null
            loaded = true
            revision++
            try { storage.clear() } catch (_: Exception) { /* Still invalidated in this process. */ }
        }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        revision++
        try {
            val encrypted = storage.read() ?: return
            val plain = cipher.decrypt(encrypted)
            val restored = try { gson.fromJson(String(plain, Charsets.UTF_8), SessionDto::class.java).toDomain() }
                finally { plain.fill(0) }
            if (restored.expiresAt.isAfter(clock.instant())) session = restored else storage.clear()
        } catch (_: Exception) {
            session = null
            try { storage.clear() } catch (_: Exception) { /* A corrupt session must never restore an account. */ }
        }
    }

    private fun storageFailure() = ApiResult.Failure(ApiFailure(FailureKind.STORAGE, message = "No se pudo guardar o borrar la sesión de forma segura."))
}
