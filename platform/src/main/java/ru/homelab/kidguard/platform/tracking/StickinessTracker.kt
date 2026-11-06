package ru.homelab.kidguard.platform.tracking

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.ContinuousUsageAccumulator
import ru.homelab.kidguard.core.domain.model.ContinuousUsageSession
import ru.homelab.kidguard.core.domain.repository.BreakRuntimeRepository
import ru.homelab.kidguard.core.domain.repository.StickinessSource
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [StickinessSource]: наблюдает то же состояние, что [ScreenTimeTracker] (экран
 * включён и разблокирован).
 *
 * Вся счётная логика живёт в чистом [ContinuousUsageAccumulator] (план «исправление перерывов»,
 * этап 3): фактические дельты монотонных часов вместо константы тика, поэтому задержанный
 * Doze-тик не теряет и не раздувает время. Трекер только поставляет часы, состояние экрана
 * и персистентность: снимок пишется в [BreakRuntimeRepository] на каждом наблюдении и при
 * старте восстанавливается — смерть процесса не обнуляет накопленное (D13).
 *
 * Переходы экрана фиксируются системными событиями (SCREEN_ON/SCREEN_OFF/USER_PRESENT) в момент
 * их наступления, а не по состоянию на ближайший тик: иначе 14 секунд погашенного экрана
 * засчитывались бы за использование.
 *
 * Управляющие флаги:
 * - [pause] — заморозка на время окна расписания (перерывов там нет, копить незачем; паузу
 *   без экрана считаем по-прежнему — ночью именно она сбрасывает цикл);
 * - [setCountingAllowed] — перерывы INTERVAL вообще применимы (включены, заданы, день подходит);
 *   false очищает цикл, чтобы включение после долгого использования не роняло замок мгновенно.
 */
@Singleton
class StickinessTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val breakRuntimeRepository: BreakRuntimeRepository
) : StickinessSource {

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)

    private val accumulator = ContinuousUsageAccumulator()

    private val _usageSession = MutableStateFlow<ContinuousUsageSession?>(null)
    override val usageSession: Flow<ContinuousUsageSession?> = _usageSession.asStateFlow()

    @Volatile
    private var paused = false

    @Volatile
    private var countingAllowed = false

    /** Защищает состояние аккумулятора и зеркало [lastSaved] от гонок тика и screen-событий. */
    private val lock = Any()

    /**
     * Зеркало того, что лежит в Room. Инициализируется ПРОЧИТАННЫМ снимком, а не null: после
     * reboot `restore()` честно отбрасывает снимок прошлой загрузки, и зеркало обязано это
     * заметить — иначе строка прошлой загрузки осталась бы в Room (регресс D11 в узком окне).
     * Доступ — только под [lock].
     */
    private var lastSaved: ContinuousUsageSession? = null

    /** [resetAfterIdleSeconds] — сколько паузы засчитываем за состоявшийся перерыв. */
    suspend fun run(resetAfterIdleSeconds: () -> Int) {
        // Восстановление раньше первого наблюдения: иначе первый же тик перезаписал бы снимок.
        val saved = try {
            breakRuntimeRepository.loadUsageSnapshot()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Снимок залипания не прочитался — начинаем с нуля")
            null
        }
        // Зеркало = фактическое содержимое Room ДО решения restore(): если reboot отбросит
        // снимок прошлой загрузки, persistIfChanged() увидит расхождение и очистит строку.
        synchronized(lock) {
            lastSaved = saved
            accumulator.restore(
                snapshot = saved,
                nowElapsedMs = SystemClock.elapsedRealtime(),
                screenActive = isUserActive()
            )
        }
        publish()
        persistIfChanged()

        val receiver = registerScreenEventReceiver {
            observe(SystemClock.elapsedRealtime(), resetAfterIdleSeconds() * 1000L)
        }
        try {
            Timber.tag(TAG).d("Счётчик залипания запущен")
            while (currentCoroutineContext().isActive) {
                delay(TICK_MS)
                observe(SystemClock.elapsedRealtime(), resetAfterIdleSeconds() * 1000L)
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun observe(nowElapsedMs: Long, resetAfterIdleMs: Long) {
        synchronized(lock) {
            accumulator.observe(
                nowElapsedMs = nowElapsedMs,
                screenActive = isUserActive(),
                paused = paused,
                countingAllowed = countingAllowed,
                resetAfterIdleMs = resetAfterIdleMs
            )
        }
        publish()
        persistIfChanged()
    }

    override fun reset() {
        synchronized(lock) {
            accumulator.reset(SystemClock.elapsedRealtime(), isUserActive())
        }
        publish()
        persistIfChanged()
    }

    override fun pause(paused: Boolean) {
        this.paused = paused
    }

    /**
     * Перерывы INTERVAL применимы сейчас (включены, настроены, день подходит по лимиту).
     * Переход в false очищает цикл немедленно: включение после долгого использования
     * в выключенном состоянии не должно ронять замок мгновенно (D11).
     */
    fun setCountingAllowed(allowed: Boolean) {
        countingAllowed = allowed
        if (!allowed) {
            synchronized(lock) { accumulator.clear() }
            publish()
            persistIfChanged()
        }
    }

    private fun publish() {
        val snapshot = synchronized(lock) { accumulator.state }
        _usageSession.value = snapshot
        val seconds = ((snapshot?.accumulatedMs ?: 0) / 1000).toInt()
        if (seconds > 0 && seconds % 60 == 0) {
            // Раз в минуту — чтобы на обкатке было видно, что счётчик реально идёт.
            Timber.tag(TAG).d("Залипание: %d мин подряд", seconds / 60)
        }
    }

    /**
     * Очередь записи снимка. Один потребитель + CONFLATED: записи не обгоняют друг друга, а при
     * пачке изменений в Room попадает последнее состояние. Иначе тик (Default) и reset/флаги
     * (другой поток) могли завершиться в обратном порядке и оставить в БД устаревший снимок.
     */
    private val persistRequests = Channel<ContinuousUsageSession?>(Channel.CONFLATED)
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        persistScope.launch {
            for (snapshot in persistRequests) {
                runCatching {
                    if (snapshot == null) breakRuntimeRepository.clearUsageSnapshot()
                    else breakRuntimeRepository.saveUsageSnapshot(snapshot)
                }.onFailure { Timber.tag(TAG).w(it, "Снимок залипания не сохранился") }
            }
        }
    }

    /**
     * Запись снимка в Room. `lastObservedElapsedMs` меняется на каждом наблюдении и персистится
     * намеренно — без него после reboot не отличить снимок прошлой загрузки; поэтому запись идёт
     * каждые [TICK_MS] и на screen-событиях, а сравнение отсекает только полные дубли.
     * Сверка и обновление зеркала — под тем же [lock], что и чтение состояния аккумулятора.
     */
    private fun persistIfChanged() {
        val snapshot = synchronized(lock) {
            val current = accumulator.state
            if (current == lastSaved) return
            lastSaved = current
            current
        }
        persistRequests.trySend(snapshot)
    }

    private fun isUserActive(): Boolean =
        powerManager?.isInteractive == true && keyguardManager?.isKeyguardLocked == false

    private fun registerScreenEventReceiver(onEvent: () -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                onEvent()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        return receiver
    }

    private companion object {
        const val TAG = "KidGuardStickiness"
        const val TICK_MS = 15_000L
    }
}
