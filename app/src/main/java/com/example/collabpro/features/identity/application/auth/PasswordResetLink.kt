package com.example.collabpro.features.identity.application.auth

import java.net.URI
import java.net.URLDecoder

sealed interface PasswordResetLink {
    class Valid(val token: String) : PasswordResetLink {
        override fun toString() = "PasswordResetLink.Valid(<redacted>)"
    }
    data object Invalid : PasswordResetLink

    companion object {
        fun parse(raw: String): PasswordResetLink = try {
            if (raw.length > 1024) Invalid else {
                val uri = URI(raw)
                val pairs = uri.rawQuery?.split('&').orEmpty()
                if (uri.scheme != "collabpro" || uri.host != "password-reset" || uri.userInfo != null || uri.port != -1 ||
                    uri.fragment != null || uri.path !in listOf("", "/") || pairs.size != 1) Invalid else {
                    val pair = pairs.single().split('=', limit = 2)
                    if (pair.size != 2 || pair[0] != "token") Invalid else {
                        val token = URLDecoder.decode(pair[1], "UTF-8")
                        if (token.isBlank() || token.length > 128 || token.any { it.isWhitespace() || it.isISOControl() }) Invalid else Valid(token)
                    }
                }
            }
        } catch (_: Exception) { Invalid }
    }
}
