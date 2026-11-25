package com.example.collabpro.features.identity.application.profile

import com.example.collabpro.core.domain.ApiFailure
import com.example.collabpro.core.domain.FailureKind
import com.example.collabpro.features.identity.domain.model.BusinessProfileUpdate

object BrandProfileValidation {
    const val NAME_LIMIT = 150
    const val DESCRIPTION_LIMIT = 2000
    const val CATEGORY_LIMIT = 150
    const val LOCATION_LIMIT = 150

    fun validate(input: BusinessProfileUpdate): ApiFailure? {
        val errors = buildMap {
            if (input.businessName.isBlank()) put("businessName", "Ingresa el nombre del negocio.")
            else if (input.businessName.length > NAME_LIMIT) put("businessName", "Máximo $NAME_LIMIT caracteres.")
            if (input.description.orEmpty().length > DESCRIPTION_LIMIT) put("description", "Máximo $DESCRIPTION_LIMIT caracteres.")
            if (input.category.orEmpty().length > CATEGORY_LIMIT) put("category", "Máximo $CATEGORY_LIMIT caracteres.")
            if (input.location.orEmpty().length > LOCATION_LIMIT) put("location", "Máximo $LOCATION_LIMIT caracteres.")
        }
        return if (errors.isEmpty()) null else ApiFailure(
            FailureKind.VALIDATION, "CLIENT_VALIDATION", "Revisa los campos del perfil.", errors
        )
    }
}
