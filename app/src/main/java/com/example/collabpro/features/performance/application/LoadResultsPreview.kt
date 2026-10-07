package com.example.collabpro.features.performance.application

import com.example.collabpro.features.performance.domain.ResultRepository

class LoadResultsPreview(private val repository: ResultRepository) {
    operator fun invoke() = repository.sampleMetrics()
}
