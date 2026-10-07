package com.example.collabpro.features.collaboration.infrastructure

import com.example.collabpro.features.collaboration.domain.*

object PreviewCollaborations : CollaborationRepository {
    override fun active() = Collaboration("Sabores que conectan", "Maki House × Camila Rojas", "En ejecución", "Entregar contenido y evidencia", "18 oct 2026")
}
