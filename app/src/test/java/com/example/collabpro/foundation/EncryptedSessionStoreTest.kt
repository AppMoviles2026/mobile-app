package com.example.collabpro.foundation

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.network.ApiJson
import com.example.collabpro.features.identity.domain.model.*
import com.example.collabpro.features.identity.infrastructure.session.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class EncryptedSessionStoreTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = SessionCipher { key }
    private val session = Session(Account(ApiFixtures.id, java.util.UUID.fromString(ApiFixtures.PROFILE_ID), "Marca", AccountType.BRAND,
        AccountStatus.ACTIVE), "private.jwt.token", ApiFixtures.now.plusSeconds(3600))
    private class MemoryStorage : EncryptedSessionStorage {
        var bytes: ByteArray? = null
        var writesAllowed = true
        override fun read() = bytes
        override fun write(encrypted: ByteArray): Boolean { if (!writesAllowed) return false; bytes = encrypted; return true }
        override fun clear(): Boolean { bytes = null; return true }
    }
    private fun store(storage: MemoryStorage, cipher: SessionCipher = this.cipher) =
        EncryptedSessionStore(storage, cipher, ApiJson.create(), ApiFixtures.clock)

    @Test fun `a persisted session is encrypted and can be restored`() = runTest {
        val storage = MemoryStorage()
        val original = store(storage)
        assertTrue(original.save(session) is ApiResult.Success)
        val raw = String(storage.bytes!!, Charsets.UTF_8)
        assertFalse(raw.contains("private.jwt.token"))
        assertFalse(raw.contains("Marca"))
        assertEquals(session, store(storage).read())
    }
    @Test fun `every save uses a different IV even for an unchanged session`() = runTest {
        val storage = MemoryStorage()
        val store = store(storage)
        store.save(session)
        val first = storage.bytes!!.clone()
        store.save(session)
        assertFalse(first.contentEquals(storage.bytes!!))
    }
    @Test fun `tampered ciphertext is rejected and removed`() = runTest {
        val storage = MemoryStorage()
        store(storage).save(session)
        storage.bytes!![storage.bytes!!.lastIndex] = (storage.bytes!!.last().toInt() xor 1).toByte()
        assertNull(store(storage).read())
        assertNull(storage.bytes)
    }
    @Test fun `a restored session with a lost keystore key is rejected`() = runTest {
        val storage = MemoryStorage()
        store(storage).save(session)
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertNull(store(storage, SessionCipher { other }).read())
        assertNull(storage.bytes)
    }
    @Test fun `clear removes persistence and invalidates old request snapshots`() = runTest {
        val storage = MemoryStorage()
        val store = store(storage)
        store.save(session)
        val old = store.credentials()!!
        assertTrue(store.clear() is ApiResult.Success)
        assertNull(storage.bytes)
        assertNull(store.read())
        assertFalse(store.isCurrent(old))
    }
    @Test fun `late invalidation does not remove a newly stored session`() = runTest {
        val storage = MemoryStorage()
        val store = store(storage)
        store.save(session)
        val old = store.credentials()!!
        val newer = session.copy(accessToken = "new.private.jwt.token")
        store.save(newer)
        store.invalidateIfCurrent(old)
        assertEquals(newer, store.read())
        assertEquals(newer, store(storage).read())
    }
    @Test fun `expired session cannot be saved`() = runTest {
        val storage = MemoryStorage()
        val result = store(storage).save(session.copy(expiresAt = ApiFixtures.now))
        assertEquals(FailureKind.UNAUTHORIZED, (result as ApiResult.Failure).error.kind)
        assertNull(storage.bytes)
    }
    @Test fun `a failed write does not invent a persisted session`() = runTest {
        val storage = MemoryStorage().apply { writesAllowed = false }
        val store = store(storage)
        assertEquals(FailureKind.STORAGE, (store.save(session) as ApiResult.Failure).error.kind)
        assertNull(store.read())
    }
    @Test fun `tokens are not exposed by model diagnostic strings`() {
        assertFalse(session.toString().contains(session.accessToken))
        val credentials = com.example.collabpro.core.application.security.SessionCredentials(
            session.account.accountId, session.accessToken, session.expiresAt, 1)
        assertFalse(credentials.toString().contains(session.accessToken))
    }
}
