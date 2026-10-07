package com.example.collabpro.features.collaboration.domain

data class Collaboration(val title: String, val partner: String, val status: String, val nextStep: String, val dueDate: String)
data class Deliverable(val title: String, val status: String, val evidence: String)
data class Incident(val subject: String, val status: String, val description: String)

interface CollaborationRepository { fun active(): Collaboration }
