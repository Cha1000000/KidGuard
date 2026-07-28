package ru.homelab.kidguard.platform.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
import ru.homelab.kidguard.platform.overlay.BreakWarningOverlay
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
 * 2. родитель ввёл верный PIN — и ночной замок, и замок перерыва снимаются до ближайшего гашения
 *    экрана (телефон отложили — замок вернётся при следующем включении, если окно ещё идёт);
 * 3. идёт экстренный звонок — иначе замок накрыл бы собой вызов, который сам же и предложил
 *    (определяем по аудио-режиму системы, см. [callActiveFlow]).
 *
 * Проверка идёт по собственному тику плюс реактивно на смену состояний. Тик заодно работает как
 * страховка — если окно замка кто-то снял, следующий проход вернёт его на место.
 */
@Singleton
class FullScreenLockController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val observeScheduleStateUseCase: ObserveScheduleStateUseCase,
    private val observeBreakStateUseCase: ObserveBreakStateUseCase,
    private val policyRepository: PolicyRepository,
    private val fullScreenLockOverlayManager: FullScreenLockOverlayManager,
    private val breakWarningOverlay: BreakWarningOverlay,
    private val stickinessSource: StickinessSource,
    private val pinGuard: PinGuard,
    private val elapsedTimeSource: ElapsedTimeSource
) {

    /** Родитель снял НОЧНОЙ замок PIN-ом; сбрасывается по гашению экрана — замок вернётся, как
     * только телефон отложат (если окно сна ещё идёт). */
    @Volatile
    private var sleepUnlockedUntilScreenOff = false

    /** Родитель снял замок перерыва PIN-ом; сбрасывается по гашению экрана и концу перерыва. */
    @Volatile
    private var breakUnlockedUntilScreenOff = false

    /** Момент (elapsedRealtime) начала экстренного звонка. Пока система поднимает вызов, аудио-режим
     * ещё не переключился в IN_CALL — на этот короткий промежуток держим замок скрытым по таймеру,
     * чтобы тик не вернул его поверх набора (дальше замок удерживает скрытым сам аудио-режим). */
    @Volatile
    private var callStartedAtMs = 0L

    /**
     * Плашку показываем один раз на перерыв. Тик контроллера идёт каждые 15 секунд, а окно
     * предупреждения длится 5 минут — без этого флага плашка мигала бы двадцать раз подряд.
     * Сбрасывается, когда предупреждение сменилось любым другим состоянием.
     */
    private var warningShown = false

    suspend fun run() {
        Timber.tag(TAG).d("Контроллер полноэкранных замков запущен")
        val screenOffReceiver = registerScreenOffReceiver()
        try {
            combine(
                observeScheduleStateUseCase(),
                observeBreakStateUseCase(),
                callActiveFlow(),
                policyRepository.emergencyContacts,
                ticker()
            ) { scheduleState, breakState, callActive, contacts, _ ->
                LockDecision(scheduleState, breakState, callActive, contacts)
            }.collect { (scheduleState, breakState, callActive, contacts) ->
                val sleep = scheduleState as? ScheduleState.Sleep
                val activeBreak = breakState as? BreakState.Active
                showWarningIfNeeded(breakState)
                // Счётчик залипания замирает на время расписания: там перерывов нет, копить незачем.
                stickinessSource.pause(scheduleState !is ScheduleState.Inactive)
                if (activeBreak == null) breakUnlockedUntilScreenOff = false

                when {
                    // Идёт экстренный звонок (или система только его поднимает) — не накрываем набор.
                    callActive ||
                        elapsedTimeSource.elapsedRealtimeMs() - callStartedAtMs < CALL_SETUP_GRACE_MS ->
                        hideIfShowing("идёт звонок — не мешаем")

                    // Сон бьёт перерыв. Отдельного правила не нужно: под расписанием breakState
                    // и так Idle, достаточно проверить сон первым.
                    sleep != null -> {
                        if (sleepUnlockedUntilScreenOff) {
                            hideIfShowing("ночной замок снят PIN-ом до гашения экрана")
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

    /** Одна плашка на перерыв: см. [warningShown]. */
    private fun showWarningIfNeeded(state: BreakState) {
        if (state !is BreakState.Warning) {
            warningShown = false
            return
        }
        if (warningShown) return
        warningShown = true
        Timber.tag(TAG).d("Показываю плашку: скоро перерыв")
        breakWarningOverlay.show(
            title = context.getString(R.string.break_warning_title),
            subtitle = context.getString(R.string.break_warning_subtitle)
        )
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
                sleepUnlockedUntilScreenOff = true
                Timber.tag(TAG).d("Верный PIN — ночной замок снят до гашения экрана")
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
                sleepUnlockedUntilScreenOff = false
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
                // Замок уводим сразу и помечаем время старта: пока система поднимает вызов и аудио-
                // режим ещё не стал IN_CALL, замок держим скрытым по таймеру (CALL_SETUP_GRACE_MS),
                // а дальше его удерживает скрытым сам аудио-режим — см. [callActiveFlow].
                callStartedAtMs = elapsedTimeSource.elapsedRealtimeMs()
                fullScreenLockOverlayManager.hide()
                Timber.tag(TAG).d("Экстренный звонок (прямой=%s)", canCallDirectly)
            }
            .onFailure { Timber.tag(TAG).w(it, "Не удалось начать звонок") }
    }

    /**
     * Идёт ли сейчас разговор — по режиму аудио системы, без `READ_PHONE_STATE`. При звонке режим
     * становится `IN_CALL`/`IN_COMMUNICATION`; так замок скрывается ровно на время вызова и
     * возвращается сразу по его завершении. Детект «диалер на переднем плане» через accessibility
     * под нашим оверлеем не работает: система не шлёт событие об окне, открывшемся под оверлеем.
     */
    private fun callActiveFlow(): Flow<Boolean> = callbackFlow {
        val audioManager = context.getSystemService(AudioManager::class.java)
        fun inCall(): Boolean = audioManager?.mode.let {
            it == AudioManager.MODE_IN_CALL || it == AudioManager.MODE_IN_COMMUNICATION
        }
        trySend(inCall())
        val listener = AudioManager.OnModeChangedListener { trySend(inCall()) }
        audioManager?.addOnModeChangedListener(context.mainExecutor, listener)
        awaitClose { audioManager?.removeOnModeChangedListener(listener) }
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
        val callActive: Boolean,
        val contacts: List<EmergencyContact>
    )

    private companion object {
        const val TAG = "KidGuardLock"

        /** Пока система поднимает вызов (аудио-режим ещё не IN_CALL), держим замок скрытым по таймеру. */
        const val CALL_SETUP_GRACE_MS = 20_000L

        /** Тик проверки: возврат замка, если его сняли. */
        const val TICK_MS = 15_000L

        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
