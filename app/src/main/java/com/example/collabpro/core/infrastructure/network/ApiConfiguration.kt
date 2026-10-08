package com.example.collabpro.core.infrastructure.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ApiConfiguration(baseUrl: String, debug: Boolean) {
    val url: HttpUrl = baseUrl.toHttpUrl()
    val isConfigured: Boolean = url.host != "unconfigured.invalid"

    init {
        require(url.isHttps || debug) { "Release API must use HTTPS" }
        require(url.encodedPath == "/api/v1/" && url.query == null && url.fragment == null)
        require(url.username.isEmpty() && url.password.isEmpty())
    }

    fun trusts(candidate: HttpUrl): Boolean = isConfigured && candidate.scheme == url.scheme &&
        candidate.host == url.host && candidate.port == url.port && candidate.encodedPath.startsWith(url.encodedPath)

    fun isProtected(candidate: HttpUrl): Boolean {
        val path = candidate.encodedPath.removePrefix(url.encodedPath)
        return path == "accounts/me" || path == "profiles/me/creator" ||
            (path.startsWith("social-accounts/") && !path.endsWith("/callback")) ||
            path == "campaigns" || path.startsWith("campaigns/") || path.startsWith("applications/")
    }
}
