package com.example.collabpro.features.identity.application

import com.example.collabpro.features.identity.domain.ProfileRepository

class LoadProfiles(private val repository: ProfileRepository) {
    fun brand() = repository.brand()
    fun creator() = repository.creator()
}
