package ru.homelab.kidguard.platform.time

import android.os.SystemClock
import ru.homelab.kidguard.core.domain.repository.ElapsedTimeSource
import javax.inject.Inject
import javax.inject.Singleton

/** `SystemClock.elapsedRealtime` — монотонен, идёт в глубоком сне, не подчиняется системным часам. */
@Singleton
class PlatformElapsedTimeSource @Inject constructor() : ElapsedTimeSource {

    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
