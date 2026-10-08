package com.example.collabpro.features.identity.application.auth

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.identity.domain.model.AccountType

object AuthInputValidation {
    private fun email(value: String) = value.trim().let { it.length in 3..254 && Regex("^[^\\s@]+@[^\\s@]+$").matches(it) }
    fun registration(type: AccountType, name: String, email: String, password: String): ApiFailure? = validate {
        if (name.trim().isBlank() || name.trim().length > 150) put(if (type == AccountType.BRAND) "businessName" else "displayName", "Ingresa un nombre de hasta 150 caracteres.")
        if (!email(email)) put("email", "Ingresa un correo electrónico válido (hasta 254 caracteres).")
        if (password.isBlank() || password.length !in 8..128) put("password", "La contraseña debe tener entre 8 y 128 caracteres.")
    }
    fun login(email: String, password: String): ApiFailure? = validate {
        if (!email(email)) put("email", "Ingresa un correo electrónico válido.")
        if (password.isBlank() || password.length > 128) put("password", "Ingresa tu contraseña (hasta 128 caracteres).")
    }
    fun recovery(email: String): ApiFailure? = validate { if (!email(email)) put("email", "Ingresa un correo electrónico válido.") }
    fun reset(password: String, confirmation: String): ApiFailure? = validate {
        if (password.isBlank() || password.length !in 8..128) put("newPassword", "La contraseña debe tener entre 8 y 128 caracteres.")
        if (password != confirmation) put("confirmation", "Las contraseñas no coinciden.")
    }
    private fun validate(block: MutableMap<String, String>.() -> Unit): ApiFailure? {
        val fields = mutableMapOf<String, String>().apply(block)
        return if (fields.isEmpty()) null else ApiFailure(FailureKind.VALIDATION, "CLIENT_VALIDATION", "Revisa los campos indicados.", fields)
    }
}
