package com.example.collabpro.features.performance.domain

data class Metric(val label: String, val value: String, val source: String, val period: String)
data class Attribution(val channel: String, val code: String, val attributedActions: String, val estimate: Boolean)

interface ResultRepository { fun sampleMetrics(): List<Metric> }
