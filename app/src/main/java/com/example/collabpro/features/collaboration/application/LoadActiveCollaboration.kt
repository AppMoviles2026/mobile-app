package com.example.collabpro.features.collaboration.application

import com.example.collabpro.features.collaboration.domain.CollaborationRepository

class LoadActiveCollaboration(private val repository: CollaborationRepository) {
    operator fun invoke() = repository.active()
}
