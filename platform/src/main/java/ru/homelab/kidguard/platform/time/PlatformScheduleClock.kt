package ru.homelab.kidguard.platform.time

import kotlinx.coroutines.delay
import ru.homelab.kidguard.core.domain.repository.ScheduleClock
import java.time.LocalDateTime
import javax.inject.Inject

/** Реальные часы: тик ровно на границе следующей минуты. */
class PlatformScheduleClock @Inject constructor() : ScheduleClock {

    override fun now(): LocalDateTime = LocalDateTime.now()

    override suspend fun awaitNextMinute() {
        val now = LocalDateTime.now()
        val elapsedInMinute = now.second * MILLIS_PER_SECOND + now.nano / NANOS_PER_MILLI
        val remaining = MILLIS_PER_MINUTE - elapsedInMinute
        delay(if (remaining <= 0L) MILLIS_PER_MINUTE else remaining)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val NANOS_PER_MILLI = 1_000_000
    }
}
