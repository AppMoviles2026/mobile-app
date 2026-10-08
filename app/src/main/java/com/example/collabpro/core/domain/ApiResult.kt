package com.example.collabpro.core.domain

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiFailure) : ApiResult<Nothing>
}

enum class FailureKind {
    NETWORK, TIMEOUT, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, VALIDATION, CONFLICT,
    REQUIREMENTS_NOT_MET, PROVIDER_UNAVAILABLE, SERVER, MALFORMED_RESPONSE,
    SESSION_CHANGED, STORAGE, CONFIGURATION
}

/** No transport exceptions, passwords or tokens cross into screen state. */
data class ApiFailure(
    val kind: FailureKind,
    val code: String? = null,
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
    val httpStatus: Int? = null
)

data class Page<T>(val items: List<T>, val total: Long, val page: Int, val size: Int)

data class PageRequest(val page: Int = 0, val size: Int = 20) {
    init {
        require(page >= 0)
        require(size in 1..100)
    }
}
