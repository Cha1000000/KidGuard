package ru.homelab.kidguard.platform.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.homelab.kidguard.core.domain.repository.BreakClock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformBreakClock @Inject constructor(
    @param:ApplicationContext private val context: Context
) : BreakClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun wallTimeMillis(): Long = System.currentTimeMillis()
    override fun localDateTime(): LocalDateTime = LocalDateTime.now()

    override fun bootCount(): Int? = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrNull()
}
