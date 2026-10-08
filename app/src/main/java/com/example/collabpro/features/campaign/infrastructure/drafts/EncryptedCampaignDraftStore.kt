package com.example.collabpro.features.campaign.infrastructure.drafts

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.application.drafts.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface CampaignDraftStorage {
    fun read(owner: UUID): ByteArray?
    fun write(owner: UUID, bytes: ByteArray): Boolean
    fun clear(owner: UUID): Boolean
}
internal class EncryptedCampaignDraftStore(private val storage: CampaignDraftStorage, private val key: () -> SecretKey,
    private val gson: Gson) : CampaignDraftStore {
    private val mutex = Mutex()
    private data class Envelope(val version: Int, val owner: UUID, val draft: CampaignDraft)
    override suspend fun load(owner: UUID): ApiResult<CampaignDraft?> = io {
        val bytes = storage.read(owner) ?: return@io ApiResult.Success(null)
        require(bytes.size in 30..512_030 && bytes[0] == 1.toByte() && bytes[1] == 12.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(2, 14))) }
        val plain = cipher.doFinal(bytes.copyOfRange(14, bytes.size))
        val envelope = gson.fromJson(plain.toString(Charsets.UTF_8), Envelope::class.java)
        require(envelope.version == 1 && envelope.owner == owner)
        val draft = envelope.draft
        require(draft.step in 1..2 && draft.conditions.requirements.size <= 50 && draft.conditions.deliverables.size <= 50)
        require(draft.conditions.zoneId.isNotBlank())
        java.time.ZoneId.of(draft.conditions.zoneId)
        require(draft.conditions.requirements.map { it.localId }.distinct().size == draft.conditions.requirements.size)
        require(draft.conditions.deliverables.map { it.localId }.distinct().size == draft.conditions.deliverables.size)
        // Gson may bypass constructors. Validate required members before allowing an external write.
        draft.basics.request()
        draft.conditions.applicationDeadline.length; draft.conditions.amount.length; draft.conditions.currency.length
        draft.conditions.compensationDescription.length; draft.conditions.compensationType.name.length
        draft.conditions.requirements.forEach { it.description.length; it.expectedValue.length; it.rule.name.length; require(it.localId.toString().length == 36) }
        draft.conditions.deliverables.forEach { it.contentType.length; it.description.length; it.quantity.length; it.deadline.length; require(it.localId.toString().length == 36) }
        require(draft.pending != DraftOperation.CREATE || draft.serverId != null || draft.creation != null)
        draft.creation?.let { require(it.request == draft.basics.request()); require(it.key.value.toString().length == 36); it.startedAt.epochSecond }
        ApiResult.Success(draft)
    }
    override suspend fun save(owner: UUID, draft: CampaignDraft): ApiResult<Unit> = io {
        val bytes = gson.toJson(Envelope(1, owner, draft)).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 512_000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        if (storage.write(owner, byteArrayOf(1, 12) + cipher.iv + cipher.doFinal(bytes))) ApiResult.Success(Unit) else storageFailure()
    }
    override suspend fun clear(owner: UUID): ApiResult<Unit> = io { if (storage.clear(owner)) ApiResult.Success(Unit) else storageFailure() }
    private suspend fun <T> io(work: () -> ApiResult<T>): ApiResult<T> = withContext(Dispatchers.IO) { mutex.withLock {
        try { work() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { storageFailure() }
    } }
    private fun storageFailure() = ApiResult.Failure(ApiFailure(FailureKind.STORAGE,
        message = "No se pudo guardar o restaurar el borrador local cifrado. Las campañas del servidor siguen disponibles en Mis campañas."))
}
