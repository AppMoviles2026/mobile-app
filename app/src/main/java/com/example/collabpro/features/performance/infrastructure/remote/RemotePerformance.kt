package com.example.collabpro.features.performance.infrastructure.remote

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.performance.domain.model.*
import java.util.UUID
import com.example.collabpro.core.infrastructure.network.*
import com.example.collabpro.features.performance.domain.repositories.PerformanceRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.security.MessageDigest

internal class RemotePerformance(private val api: PerformanceApi, private val executor: ApiExecutor) : PerformanceRepository {
    override suspend fun metrics(id: UUID, page: PageRequest) =
        executor.protectedCall({ api.metrics(it, id, page.page, page.size) }) { it.model(id, page) }

    override suspend fun refresh(id: UUID, body: RefreshMetrics) =
        executor.protectedCall({ api.refresh(it, id, body) }) { it.refresh.required("refresh") }

    override suspend fun snapshots(id: UUID, page: PageRequest) =
        executor.protectedCall({ api.snapshots(it, id, page.page, page.size) }) { it.model(page) }

    override suspend fun evidence(id: UUID, page: PageRequest, status: String?) =
        executor.protectedCall({ api.evidence(it, id, page.page, page.size, status) }) { it.model(page).also { result -> require(result.items.all { row -> row.collaborationId == id }) } }

    override suspend fun evidenceDetail(id: UUID) =
        executor.protectedCall({ api.evidenceDetail(it, id) }) { it.also { row -> require(row.id == id) } }

    override suspend fun register(id: UUID, body: RegisterEvidence) =
        executor.protectedCall({ api.register(it, id, body) }) { it.evidence.required("evidence").also { row -> require(row.collaborationId == id) } }

    override suspend fun review(id: UUID, body: ReviewEvidence) =
        executor.protectedCall({ api.review(it, id, body) }) { it.evidence.required("evidence").also { row -> require(row.id == id) } }

    override suspend fun upload(id: UUID, key: UUID, file: Document): ApiResult<Asset> {
        val part = MultipartBody.Part.createFormData("file", file.name, file.bytes.toRequestBody(file.mediaType.toMediaType()))
        return executor.protectedCall({ api.upload(it, id, key, part) }) { it.asset.required("asset") }
    }

    override suspend fun asset(id: UUID) =
        executor.protectedCall({ api.asset(it, id) }) { it.asset.required("asset").also { asset -> require(asset.id == id) } }

    override suspend fun download(asset: Asset) =
        executor.protectedCall({ api.download(it, asset.id) }) { readContent(it, asset) }

    override suspend fun destinations() =
        executor.protectedCall({ api.destinations(it) }) { it }

    override suspend fun attribution(id: UUID, page: PageRequest, period: Period?) =
        executor.protectedCall({ api.attribution(it, id, page.page, page.size, period?.start, period?.end) }) { it.model(id, page) }

    override suspend fun create(id: UUID, body: CreateMechanism) =
        executor.protectedCall({ api.create(it, id, body) }) { it.mechanism.required("mechanism").also { row -> require(row.collaborationId == id) } }

    override suspend fun change(id: UUID, body: ChangeMechanism) =
        executor.protectedCall({ api.change(it, id, body) }) { it.mechanism.required("mechanism").also { row -> require(row.id == id) } }

    override suspend fun record(id: UUID, body: RecordInteraction) =
        executor.protectedCall({ api.record(it, id, body) }) { it.interaction.required("interaction").also { row -> require(row.attributionId == id) } }

    override suspend fun interactions(id: UUID, page: PageRequest, period: Period?) =
        executor.protectedCall({ api.interactions(it, id, page.page, page.size, period?.start, period?.end) }) { it.model(page).also { result -> require(result.items.all { row -> row.attributionId == id }) } }
}

private fun readContent(response: ResponseBody, asset: Asset): ByteArray = response.use { body ->
    require(asset.byteSize in 1..MAX_ASSET_BYTES && body.contentLength() in listOf(-1L, asset.byteSize))
    val source = body.source()
    val bytes = source.readByteArray(asset.byteSize)
    require(source.exhausted())
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    require(digest.equals(asset.sha256, ignoreCase = true))
    bytes
}

internal const val MAX_ASSET_BYTES = 10L * 1024 * 1024
