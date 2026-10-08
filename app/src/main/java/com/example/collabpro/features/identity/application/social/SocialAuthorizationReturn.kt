package com.example.collabpro.features.identity.application.social

import com.example.collabpro.features.identity.domain.model.SocialPlatform
import java.net.URI
import java.util.UUID

/** An untrusted return can request a read, never assert success or carry provider credentials. */
object SocialAuthorizationReturn {
    fun parse(raw: String): UUID? = runCatching {
        if (raw.length > 256) return null
        val uri = URI(raw)
        if (uri.scheme != "collabpro" || uri.host != "social-authorization-completed" ||
            uri.userInfo != null || uri.port != -1 || uri.fragment != null || uri.path !in listOf("", "/")) return null
        val query = uri.rawQuery ?: return null
        val prefix = "authorizationId="
        if (!query.startsWith(prefix)) return null
        val value = query.removePrefix(prefix)
        val id = UUID.fromString(value)
        id.takeIf { it.toString() == value.lowercase() }
    }.getOrNull()

    fun trustedBrowserUrl(uri: URI, platform: SocialPlatform): Boolean {
        val host = when (platform) {
            SocialPlatform.INSTAGRAM -> "www.instagram.com"
            SocialPlatform.TIKTOK -> "www.tiktok.com"
        }
        return uri.scheme == "https" && uri.host == host && uri.userInfo == null &&
            uri.port in listOf(-1, 443) && uri.fragment == null
    }
}
