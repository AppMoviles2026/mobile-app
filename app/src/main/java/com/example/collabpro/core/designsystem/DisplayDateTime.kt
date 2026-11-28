package com.example.collabpro.core.designsystem

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val displayDateTime = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm")

fun Instant.displayTime(zone: ZoneId = ZoneId.systemDefault()): String = atZone(zone).format(displayDateTime)
