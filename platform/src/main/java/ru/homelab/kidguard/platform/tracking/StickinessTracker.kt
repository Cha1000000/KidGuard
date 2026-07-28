package ru.homelab.kidguard.platform.tracking

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import ru.homelab.kidguard.core.domain.repository.StickinessSource
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [StickinessSource]: наблюдает то же состояние, что [ScreenTimeTracker] (экран
 * включён и разблокирован), своим тиком раз в [TICK_SECONDS] секунд.
 *
 * Внутри окна расписания счётчик **замирает** — контроллер задачи 7 передаёт признак через
 * [pause]; обнулять его там не нужно: ночью экран всё равно погаснет и сработает общее правило
 * паузы, а после учёбы залипание честно продолжится с накопленного. Порог паузы читается лямбдой
 * в [run], потому что зависит от текущей длительности перерыва (её родитель может поменять на лету).
 */
@Singleton
class StickinessTracker @Inject constructor(
    @param:ApplicationContext private val context: Context
) : StickinessSource {

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)

    private val _stickySeconds = MutableStateFlow(0)
    override val stickySeconds: Flow<Int> = _stickySeconds.asStateFlow()

    private var idleSeconds = 0
    @Volatile private var paused = false

    /** [resetAfterIdleSeconds] — сколько паузы засчитываем за состоявшийся перерыв. */
    suspend fun run(resetAfterIdleSeconds: () -> Int) {
        Timber.tag(TAG).d("Счётчик залипания запущен")
        while (currentCoroutineContext().isActive) {
            delay(TICK_SECONDS * 1000L)
            if (isUserActive()) {
                idleSeconds = 0
                if (!paused) {
                    _stickySeconds.value += TICK_SECONDS
                    // Раз в минуту — чтобы на обкатке было видно, что счётчик реально идёт.
                    if (_stickySeconds.value % 60 == 0) {
                        Timber.tag(TAG).d("Залипание: %d мин подряд", _stickySeconds.value / 60)
                    }
                }
            } else {
                // Паузу копим всегда, даже под расписанием: ночью именно она обнуляет счётчик.
                idleSeconds += TICK_SECONDS
                val threshold = resetAfterIdleSeconds()
                if (threshold > 0 && idleSeconds >= threshold) reset()
            }
        }
    }

    override fun reset() {
        _stickySeconds.value = 0
        idleSeconds = 0
    }

    override fun pause(paused: Boolean) {
        this.paused = paused
    }

    private fun isUserActive(): Boolean =
        powerManager?.isInteractive == true && keyguardManager?.isKeyguardLocked == false

    private companion object {
        const val TAG = "KidGuardStickiness"
        const val TICK_SECONDS = 15
    }
}
