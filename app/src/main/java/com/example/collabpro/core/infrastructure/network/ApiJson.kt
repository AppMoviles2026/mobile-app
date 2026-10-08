package com.example.collabpro.core.infrastructure.network

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.Instant
import java.util.UUID

object ApiJson {
    fun create(): Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, object : TypeAdapter<Instant>() {
            override fun write(out: JsonWriter, value: Instant) { out.value(value.toString()) }
            override fun read(reader: JsonReader): Instant = try { Instant.parse(reader.nextString()) }
                catch (error: Exception) { throw JsonParseException("Invalid ISO-8601 instant", error) }
        }.nullSafe())
        .create()
}

fun <T : Any> T?.required(field: String): T = this ?: throw InvalidApiResponse("Missing $field")
fun String?.uuid(field: String): UUID {
    val raw = required(field)
    val value = UUID.fromString(raw)
    if (!value.toString().equals(raw, ignoreCase = true)) throw InvalidApiResponse("Invalid $field UUID")
    return value
}
inline fun <reified T : Enum<T>> String?.enum(field: String): T = enumValueOf(required(field))
