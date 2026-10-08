package com.example.collabpro.features.identity.application.profile

import com.example.collabpro.core.domain.ApiFailure
import com.example.collabpro.core.domain.FailureKind
import com.example.collabpro.features.identity.domain.model.CreatorProfileUpdate

object CreatorProfileValidation {
    fun validate(input: CreatorProfileUpdate): ApiFailure? {
        val errors = buildMap {
            if (input.displayName.isBlank()) put("displayName", "Ingresa tu nombre público.")
            else if (input.displayName.length > 150) put("displayName", "Máximo 150 caracteres.")
            if ((input.biography?.length ?: 0) > 2000) put("biography", "Máximo 2000 caracteres.")
            if ((input.niche?.length ?: 0) > 150) put("niche", "Máximo 150 caracteres.")
            if ((input.audienceDescription?.length ?: 0) > 2000) put("audienceDescription", "Máximo 2000 caracteres.")
            if ((input.location?.length ?: 0) > 150) put("location", "Máximo 150 caracteres.")
        }
        return if (errors.isEmpty()) null else ApiFailure(FailureKind.VALIDATION, "CLIENT_VALIDATION",
            "Revisa los campos del perfil.", errors)
    }
}
