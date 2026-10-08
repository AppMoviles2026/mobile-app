package com.example.collabpro.campaign

import com.example.collabpro.core.domain.*
import com.example.collabpro.core.infrastructure.network.ApiJson
import com.example.collabpro.features.campaign.application.drafts.*
import com.example.collabpro.features.campaign.domain.model.IdempotencyKey
import com.example.collabpro.features.campaign.infrastructure.drafts.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID
import javax.crypto.KeyGenerator

class EncryptedCampaignDraftStoreTest {
    private val owner = UUID.randomUUID()
    private var key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val bytes = mutableMapOf<UUID, ByteArray>()
    private var fail = false
    private val storage = object : CampaignDraftStorage {
        override fun read(owner: UUID) = bytes[owner]
        override fun write(owner: UUID, value: ByteArray): Boolean { if (fail) return false; bytes[owner] = value; return true }
        override fun clear(owner: UUID): Boolean { if (fail) return false; bytes.remove(owner); return true }
    }
    private val store = EncryptedCampaignDraftStore(storage, { key }, ApiJson.create())
    @Test fun `partial creation round trips UUID exact intent and fields without plaintext`() = runTest {
        val draft = CampaignFixtures.draft.copy(pending = DraftOperation.CREATE,
            creation = CreationIntent(CampaignFixtures.basics.request(), IdempotencyKey(), Instant.parse("2030-01-01T00:00:00Z")))
        assertTrue(store.save(owner, draft) is ApiResult.Success)
        assertFalse(bytes[owner]!!.toString(Charsets.UTF_8).contains("Mostrar producto"))
        assertEquals(draft, (store.load(owner) as ApiResult.Success).value)
    }
    @Test fun `every save uses a fresh initialization vector`() = runTest {
        store.save(owner, CampaignFixtures.draft); val first = bytes[owner]!!.copyOf()
        store.save(owner, CampaignFixtures.draft); assertFalse(first.contentEquals(bytes[owner]!!))
    }
    @Test fun `different owners cannot read copied ciphertext`() = runTest {
        store.save(owner, CampaignFixtures.draft); val other = UUID.randomUUID(); bytes[other] = bytes[owner]!!
        assertEquals(FailureKind.STORAGE, (store.load(other) as ApiResult.Failure).error.kind)
    }
    @Test fun `corruption and missing key do not return empty draft success`() = runTest {
        store.save(owner, CampaignFixtures.draft); bytes[owner]!![18] = (bytes[owner]!![18].toInt() xor 1).toByte()
        assertTrue(store.load(owner) is ApiResult.Failure)
        store.save(owner, CampaignFixtures.draft); key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertTrue(store.load(owner) is ApiResult.Failure)
    }
    @Test fun `disk failures block checkpoints and do not claim data was cleared`() = runTest {
        store.save(owner, CampaignFixtures.draft); fail = true
        assertTrue(store.save(owner, CampaignDraft()) is ApiResult.Failure); assertTrue(store.clear(owner) is ApiResult.Failure)
        assertEquals(CampaignFixtures.draft, (store.load(owner) as ApiResult.Success).value)
    }
    @Test fun `clear removes only selected owner`() = runTest {
        val other = UUID.randomUUID(); store.save(owner, CampaignFixtures.draft); store.save(other, CampaignDraft())
        store.clear(owner); assertNull((store.load(owner) as ApiResult.Success).value); assertNotNull((store.load(other) as ApiResult.Success).value)
    }
}
