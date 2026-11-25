package com.example.collabpro.features.collaboration.application

import com.example.collabpro.features.collaboration.domain.model.ContentReference
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object CollaborationInputs {
    private val date = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)

    fun deadline(text: String, zone: ZoneId = ZoneId.systemDefault()): Instant? = runCatching {
        val local = LocalDateTime.parse(text.trim(), date)
        local.toInstant(zone.rules.getValidOffsets(local).single())
    }.getOrNull()

    fun externalUrl(text: String): String? = text.trim().takeIf { value ->
        value.length in 1..2048 && runCatching {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) && uri.host != null && uri.userInfo == null
        }.getOrDefault(false)
    }

    fun references(lines: String, attachments: List<ContentReference>): List<ContentReference>? {
        val urls = buildList {
            for (line in lines.lineSequence().filter { it.isNotBlank() }) {
                add(ContentReference(url = externalUrl(line) ?: return null))
            }
        }
        return (urls + attachments).distinct().takeIf { it.size <= 10 }
    }
}
