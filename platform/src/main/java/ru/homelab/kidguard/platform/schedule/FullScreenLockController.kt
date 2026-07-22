package ru.homelab.kidguard.platform.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import ru.homelab.kidguard.core.domain.model.BreakState
import ru.homelab.kidguard.core.domain.model.EmergencyContact
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.repository.ElapsedTimeSource
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.StickinessSource
import ru.homelab.kidguard.core.domain.security.PinGuard
import ru.homelab.kidguard.core.domain.usecase.ObserveBreakStateUseCase
import ru.homelab.kidguard.core.domain.usecase.ObserveScheduleStateUseCase
import ru.homelab.kidguard.platform.R
import ru.homelab.kidguard.platform.accessibility.ForegroundAppMonitor
import ru.homelab.kidguard.platform.overlay.FullScreenLockOverlayManager
import ru.homelab.kidguard.platform.overlay.LockAppearance
import timber.log.Timber
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единственный владелец полноэкранного замка: держит его и во «Время сна», и во время перерыва.
 *
 * Почему один контроллер на два сценария: окно замка одно, и два независимых владельца дрались бы
 * за него — кто последний в тике, тот и прав. Гонка такого рода на эмуляторе может не проявиться,
 * а на реальном устройстве проявится.
 *
 * В отличие от [ru.homelab.kidguard.platform.overlay.BlockingController], решение НЕ зависит от
 * активного приложения: замок должен накрывать и рабочий стол, а `shouldBlock` первым делом
 * пропускает лаунчер. Поэтому — отдельный контроллер поверх состояния расписания и перерывов.
 *
 * Приоритет: **сон бьёт перерыв**. Отдельного правила для этого не нужно — внутри любого окна
 * расписания состояние перерыва и так `Idle` (см. `breakStateAt`), так что достаточно проверить
 * сон первым.
 *
 * Замок уходит в случаях:
 * 1. окно сна закончилось / перерыв доиграл свой таймер;
 * 2. родитель ввёл верный PIN — ночью замка нет [UNLOCK_WINDOW_MS] (15 минут), а на перерыве до
 *    ближайшего гашения экрана: перерыв и так короткий, 15-минутное окно съело бы его целиком;
 * 3. открыт телефон — иначе замок накрыл бы собой экстренный звонок, который сам же и предложил.
 *
 * Проверка идёт по собственному тику: окно разблокировки истекает от хода часов, а не от событий
 * в приложении. Тик заодно работает как страховка — если окно замка кто-то снял, следующий
 * проход вернёт его на место.
 */
@Singleton
class FullScreenLockController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val observeScheduleStateUseCase: ObserveScheduleStateUseCase,
    private val observeBreakStateUseCase: ObserveBreakStateUseCase,
    private val policyRepository: PolicyRepository,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val fullScreenLockOverlayManager: FullScreenLockOverlayManager,
    private val stickinessSource: StickinessSource,
    private val pinGuard: PinGuard,
    private val elapsedTimeSource: ElapsedTimeSource
) {

    /** Момент (elapsedRealtime), до которого ночной замок не показываем после верного PIN. */
    private var unlockedUntilMs: Long = 0L

    /** Родитель снял замок перерыва PIN-ом; сбрасывается по гашению экрана и концу перерыва. */
    @Volatile
    private var breakUnlockedUntilScreenOff = false

    suspend fun run() {
        Timber.tag(TAG).d("Контроллер полноэкранных замков запущен")
        val screenOffReceiver = registerScreenOffReceiver()
        try {
            combine(
                observeScheduleStateUseCase(),
                observeBreakStateUseCase(),
                foregroundAppMonitor.currentPackage,
                policyRepository.emergencyContacts,
                ticker()
            ) { scheduleState, breakState, activePackage, contacts, _ ->
                LockDecision(scheduleState, breakState, activePackage, contacts)
            }.collect { (scheduleState, breakState, activePackage, contacts) ->
                val sleep = scheduleState as? ScheduleState.Sleep
                val activeBreak = breakState as? BreakState.Active
                // Счётчик залипания замирает на время расписания: там перерывов нет, копить незачем.
                stickinessSource.pause(scheduleState !is ScheduleState.Inactive)
                if (activeBreak == null) breakUnlockedUntilScreenOff = false

                when {
                    activePackage != null && activePackage in dialerPackages ->
                        hideIfShowing("открыт телефон — не мешаем звонку")

                    // Сон бьёт перерыв. Отдельного правила не нужно: под расписанием breakState
                    // и так Idle, достаточно проверить сон первым.
                    sleep != null -> {
                        if (elapsedTimeSource.elapsedRealtimeMs() < unlockedUntilMs) {
                            hideIfShowing("родитель разблокировал PIN-ом")
                        } else {
                            showSleepLock(sleep, contacts)
                        }
                    }

                    activeBreak != null -> {
                        if (breakUnlockedUntilScreenOff) {
                            hideIfShowing("перерыв снят PIN-ом до гашения экрана")
                        } else {
                            showBreakLock(activeBreak, contacts)
                        }
                    }

                    else -> hideIfShowing("блокировать нечего")
                }
            }
        } finally {
            runCatching { context.unregisterReceiver(screenOffReceiver) }
            stickinessSource.pause(false)
        }
    }

    private fun showSleepLock(sleep: ScheduleState.Sleep, contacts: List<EmergencyContact>) {
        if (fullScreenLockOverlayManager.isShowing()) return
        Timber.tag(TAG).d("Показываю ночной замок до %s", sleep.endsAt)
        fullScreenLockOverlayManager.show(
            appearance = LockAppearance.NIGHT,
            title = context.getString(R.string.sleep_lock_title),
            subtitle = context.getString(
                R.string.sleep_lock_until, sleep.endsAt.format(TIME_FORMATTER)
            ),
            countdownSeconds = null,
            contacts = contacts,
            verifyPin = pinGuard::verify,
            onUnlocked = {
                unlockedUntilMs = elapsedTimeSource.elapsedRealtimeMs() + UNLOCK_WINDOW_MS
                Timber.tag(TAG).d("Верный PIN — замка не будет %d минут", UNLOCK_WINDOW_MS / 60_000)
            },
            onCall = ::callContact
        )
    }

    private fun showBreakLock(state: BreakState.Active, contacts: List<EmergencyContact>) {
        if (fullScreenLockOverlayManager.isShowing()) return
        Timber.tag(TAG).d("Показываю замок перерыва, осталось %d с", state.secondsLeft)
        fullScreenLockOverlayManager.show(
            appearance = LockAppearance.BREAK,
            title = context.getString(R.string.break_lock_title),
            // Родитель мог не заполнить поле — тогда показываем фразу-шаблон, иначе ребёнок
            // увидел бы голый отсчёт без объяснения, зачем его прервали.
            subtitle = state.message.ifBlank { context.getString(R.string.break_lock_default_message) },
            countdownSeconds = state.secondsLeft,
            contacts = contacts,
            verifyPin = pinGuard::verify,
            onUnlocked = {
                breakUnlockedUntilScreenOff = true
                Timber.tag(TAG).d("Верный PIN — замок перерыва снят до гашения экрана")
            },
            onCall = ::callContact
        )
    }

    /**
     * Замок перерыва возвращается, как только экран погас. `ACTION_SCREEN_OFF` приходит и от кнопки
     * питания, и от автогашения по таймауту — различить нельзя, да и не нужно: оба означают
     * «телефон отложили».
     */
    private fun registerScreenOffReceiver(): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                breakUnlockedUntilScreenOff = false
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return receiver
    }

    private fun hideIfShowing(reason: String) {
        if (!fullScreenLockOverlayManager.isShowing()) return
        Timber.tag(TAG).d("Убираю замок: %s", reason)
        fullScreenLockOverlayManager.hide()
    }

    /**
     * Звонок экстренному контакту. `ACTION_CALL` набирает сразу, но требует CALL_PHONE; если
     * родитель пропустил этот шаг в мастере, откатываемся на `ACTION_DIAL` — он лишь открывает
     * телефон с готовым номером и разрешений не требует. Так кнопка работает всегда.
     */
    private fun callContact(contact: EmergencyContact) {
        val canCallDirectly = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (canCallDirectly) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, "tel:${contact.phone}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onSuccess {
                // Замок уводим сразу, не дожидаясь тика: иначе он накроет экран вызова.
                fullScreenLockOverlayManager.hide()
                Timber.tag(TAG).d("Экстренный звонок (прямой=%s)", canCallDirectly)
            }
            .onFailure { Timber.tag(TAG).w(it, "Не удалось начать звонок") }
    }

    /**
     * Пакеты, которым разрешено «пробить» замок. Спрашиваем у системы (штатный телефон +
     * обработчик `tel:`), а не хардкодим: на HiOS/MIUI диалер называется по-своему.
     */
    private val dialerPackages: Set<String> by lazy {
        buildSet {
            runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
                .getOrNull()?.let(::add)
            runCatching {
                context.packageManager
                    .resolveActivity(Intent(Intent.ACTION_DIAL), PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName
            }.getOrNull()?.takeIf { it.isNotBlank() && it != ANDROID_RESOLVER_PACKAGE }?.let(::add)
        }.also { Timber.tag(TAG).d("Пакеты телефона: %s", it) }
    }

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    private data class LockDecision(
        val scheduleState: ScheduleState,
        val breakState: BreakState,
        val activePackage: String?,
        val contacts: List<EmergencyContact>
    )

    private companion object {
        const val TAG = "KidGuardLock"

        /** Сколько замка нет после верного PIN. */
        const val UNLOCK_WINDOW_MS = 15L * 60 * 1000

        /** Тик проверки: истечение окна разблокировки и возврат замка, если его сняли. */
        const val TICK_MS = 15_000L

        const val ANDROID_RESOLVER_PACKAGE = "android"
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
