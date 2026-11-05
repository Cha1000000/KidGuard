package ru.homelab.kidguard.core.domain.repository

import java.time.LocalDateTime

interface BreakClock {
    fun elapsedRealtimeMs(): Long
    fun wallTimeMillis(): Long
    fun localDateTime(): LocalDateTime
    fun bootCount(): Int?
}
