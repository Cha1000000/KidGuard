package ru.homelab.kidguard.platform.schedule

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import ru.homelab.kidguard.core.domain.model.EmergencyContact
import ru.homelab.kidguard.core.domain.model.ScheduleState
import ru.homelab.kidguard.core.domain.repository.ElapsedTimeSource
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.security.PinGuard
import ru.homelab.kidguard.core.domain.usecase.ObserveScheduleStateUseCase
import ru.homelab.kidguard.platform.accessibility.ForegroundAppMonitor
import ru.homelab.kidguard.platform.overlay.SleepLockOverlayManager
import timber.log.Timber
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Держит ночной замок, пока идёт «Время сна».
 *
 * В отличие от [ru.homelab.kidguard.platform.overlay.BlockingController], решение НЕ зависит от
 * активного приложения: замок должен накрывать и рабочий стол, а `shouldBlock` первым делом
 * пропускает лаунчер. Поэтому — отдельный контроллер поверх состояния расписания.
 *
 * Замок уходит в трёх случаях:
 * 1. окно сна закончилось;
 * 2. родитель ввёл верный PIN — тогда замка нет [UNLOCK_WINDOW_MS] (15 минут), после чего он
 *    возвращается сам: родителю не нужно помнить, что защиту надо включить обратно;
 * 3. открыт телефон — иначе замок накрыл бы собой экстренный звонок, который сам же и предложил.
 *
 * Проверка идёт по собственному тику: окно разблокировки истекает от хода часов, а не от событий
 * в приложении. Тик заодно работает как страховка — если окно замка кто-то снял, следующий
 * проход вернёт его на место.
 */
@Singleton
class SleepLockController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val observeScheduleStateUseCase: ObserveScheduleStateUseCase,
    private val policyRepository: PolicyRepository,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    private val sleepLockOverlayManager: SleepLockOverlayManager,
    private val pinGuard: PinGuard,
    private val elapsedTimeSource: ElapsedTimeSource
) {

    /** Момент (elapsedRealtime), до которого замок не показываем после верного PIN. */
    private var unlockedUntilMs: Long = 0L

    suspend fun run() {
        Timber.tag(TAG).d("Контроллер ночного замка запущен")
        combine(
            observeScheduleStateUseCase(),
            foregroundAppMonitor.currentPackage,
            policyRepository.emergencyContacts,
            ticker()
        ) { scheduleState, activePackage, contacts, _ ->
            LockDecision(scheduleState, activePackage, contacts)
        }.collect { (scheduleState, activePackage, contacts) ->
            val sleep = scheduleState as? ScheduleState.Sleep
            when {
                sleep == null -> hideIfShowing("окно сна закончилось")
                elapsedTimeSource.elapsedRealtimeMs() < unlockedUntilMs ->
                    hideIfShowing("родитель разблокировал PIN-ом")
                activePackage != null && activePackage in dialerPackages ->
                    hideIfShowing("открыт телефон — не мешаем звонку")
                else -> showLock(sleep, contacts)
            }
        }
    }

    private fun showLock(sleep: ScheduleState.Sleep, contacts: List<EmergencyContact>) {
        if (sleepLockOverlayManager.isShowing()) return
        Timber.tag(TAG).d("Показываю ночной замок до %s", sleep.endsAt)
        sleepLockOverlayManager.show(
            untilText = sleep.endsAt.format(TIME_FORMATTER),
            contacts = contacts,
            verifyPin = pinGuard::verify,
            onUnlocked = {
                unlockedUntilMs = elapsedTimeSource.elapsedRealtimeMs() + UNLOCK_WINDOW_MS
                Timber.tag(TAG).d("Верный PIN — замка не будет %d минут", UNLOCK_WINDOW_MS / 60_000)
            },
            onCall = ::callContact
        )
    }

    private fun hideIfShowing(reason: String) {
        if (!sleepLockOverlayManager.isShowing()) return
        Timber.tag(TAG).d("Убираю ночной замок: %s", reason)
        sleepLockOverlayManager.hide()
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
                sleepLockOverlayManager.hide()
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
        val activePackage: String?,
        val contacts: List<EmergencyContact>
    )

    private companion object {
        const val TAG = "KidGuardSleepLock"

        /** Сколько замка нет после верного PIN. */
        const val UNLOCK_WINDOW_MS = 15L * 60 * 1000

        /** Тик проверки: истечение окна разблокировки и возврат замка, если его сняли. */
        const val TICK_MS = 15_000L

        const val ANDROID_RESOLVER_PACKAGE = "android"
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
