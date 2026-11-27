package com.example.collabpro.features.performance.domain.repositories

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.performance.domain.model.*
import java.util.UUID

interface PerformanceRepository {
    suspend fun metrics(id: UUID, page: PageRequest): ApiResult<MetricsReport>
    suspend fun refresh(id: UUID, body: RefreshMetrics): ApiResult<Refresh>
    suspend fun snapshots(id: UUID, page: PageRequest): ApiResult<Page<Snapshot>>
    suspend fun evidence(id: UUID, page: PageRequest, status: String?): ApiResult<Page<Evidence>>
    suspend fun evidenceDetail(id: UUID): ApiResult<Evidence>
    suspend fun register(id: UUID, body: RegisterEvidence): ApiResult<Evidence>
    suspend fun review(id: UUID, body: ReviewEvidence): ApiResult<Evidence>
    suspend fun upload(id: UUID, key: UUID, file: Document): ApiResult<Asset>
    suspend fun asset(id: UUID): ApiResult<Asset>
    suspend fun download(asset: Asset): ApiResult<ByteArray>
    suspend fun destinations(): ApiResult<List<Destination>>
    suspend fun attribution(id: UUID, page: PageRequest, period: Period?): ApiResult<AttributionReport>
    suspend fun create(id: UUID, body: CreateMechanism): ApiResult<Mechanism>
    suspend fun change(id: UUID, body: ChangeMechanism): ApiResult<Mechanism>
    suspend fun record(id: UUID, body: RecordInteraction): ApiResult<Interaction>
    suspend fun interactions(id: UUID, page: PageRequest, period: Period?): ApiResult<Page<Interaction>>
}
