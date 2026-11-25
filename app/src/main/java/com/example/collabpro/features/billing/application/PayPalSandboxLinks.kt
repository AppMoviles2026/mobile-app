package com.example.collabpro.features.billing.application

import java.net.URI
import java.util.UUID

enum class PayPalReturnKind(val host: String, val idField: String) {
    LINK("billing-return", "authorizationId"),
    SUBSCRIPTION("subscription-return", "subscriptionId"),
    COMPENSATION("compensation-return", "collaborationId")
}

data class PayPalReturn(val kind: PayPalReturnKind, val id: UUID)

object PayPalSandboxLinks {
    fun approval(raw: String?): URI? {
        val uri = parse(raw) ?: return null
        return uri.takeIf {
            it.scheme == "https" && it.host in setOf("sandbox.paypal.com", "www.sandbox.paypal.com") &&
                it.rawUserInfo == null && it.port == -1 && it.rawFragment == null
        }
    }

    fun returned(raw: String?): PayPalReturn? {
        val uri = parse(raw) ?: return null
        if (uri.scheme != "collabpro" || uri.rawUserInfo != null || uri.port != -1 ||
            !uri.rawPath.isNullOrEmpty() || uri.rawFragment != null) return null
        val kind = PayPalReturnKind.entries.find { it.host == uri.host } ?: return null
        val query = uri.rawQuery ?: return null
        val fields = linkedMapOf<String, String>()
        for (part in query.split('&')) {
            val pair = part.split('=', limit = 2)
            if (pair.size != 2 || pair[0] !in setOf(kind.idField, "status", "cancelled") ||
                fields.put(pair[0], pair[1]) != null) return null
        }
        val value = fields[kind.idField] ?: return null
        val id = runCatching { UUID.fromString(value) }.getOrNull() ?: return null
        if (!id.toString().equals(value, ignoreCase = true)) return null
        return PayPalReturn(kind, id)
    }

    private fun parse(raw: String?) = raw?.takeIf { it.length <= 4000 }
        ?.let { runCatching { URI(it) }.getOrNull() }
}
