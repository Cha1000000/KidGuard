package ru.homelab.kidguard.platform.tracking

import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.UsageEventSource
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import ru.homelab.kidguard.core.domain.repository.UsageWatermarkRepository
import ru.homelab.kidguard.core.domain.usecase.ObserveAppLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.ObserveBonusPassesUseCase
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.UsageBucket
import ru.homelab.kidguard.core.domain.usecase.UsageInterval
import ru.homelab.kidguard.core.domain.usecase.countsTowardsDailyLimit
import ru.homelab.kidguard.core.domain.usecase.foldUsageEvents
import ru.homelab.kidguard.core.domain.usecase.splitByDate
import ru.homelab.kidguard.core.domain.usecase.usageTickTargets
import ru.homelab.kidguard.platform.apps.AlwaysAllowedPackages
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Досчитывает экранное время, прошедшее мимо учёта, пока контроль не работал.
 *
 * Смысл — убрать выгоду от убийства приложения. Пока контроль мёртв, наш собственный счётчик стоит,
 * и 06.09.2026 это дало ребёнку 101 минуту игры сверх лимита бесплатно. Система же ведёт
 * статистику сама, и доступ к ней переживает force-stop — значит провал можно закрыть задним
 * числом, как только приложение подняли. Работает против любого способа убийства, а не только
 * против кнопки «Очистить всё».
 *
 * Живёт рядом с [ScreenTimeTracker] и намеренно пишет через те же методы репозитория и те же
 * правила ([usageTickTargets], [countsTowardsDailyLimit]): досчитанная минута обязана попасть
 * ровно в тот же счётчик, что и минута, посчитанная вживую. Своя копия правил здесь однажды
 * разошлась бы с настоящими.
 */
@Singleton
class UsageBackfiller @Inject constructor(
    private val usageEventSource: UsageEventSource,
    private val usageRepository: UsageRepository,
    private val policyRepository: PolicyRepository,
    private val watermarkRepository: UsageWatermarkRepository,
    private val alwaysAllowedPackages: AlwaysAllowedPackages,
    private val observeLimitState: ObserveLimitStateUseCase,
    private val observeAppLimitState: ObserveAppLimitStateUseCase,
    private val observeBonusPasses: ObserveBonusPassesUseCase
) {

    /**
     * Итог досчёта. Ребёнку и родителю его не показываем (решение Володи 06.09.2026: списывается
     * ровно то время, которое ребёнок и правда потратил, оправдываться не за что) — итог нужен
     * логу, чтобы при разборе очередного инцидента было видно, какой провал закрыли и чем.
     */
    data class Result(val from: Instant, val to: Instant, val secondsByDate: Map<LocalDate, Int>) {
        val totalMinutes: Int get() = secondsByDate.values.sum() / 60
    }

    /**
     * Закрывает разрыв между ватерлинией и [now]. Возвращает `null`, если досчитывать нечего
     * (учёт ещё ни разу не работал, разрыв мал, доступа к статистике нет).
     *
     * Ватерлиния двигается в любом случае — иначе один и тот же провал предлагался бы к досчёту
     * снова и снова, а при выданном доступе время бы задвоилось.
     */
    suspend fun backfill(now: Instant = Instant.now()): Result? {
        val from = watermarkRepository.accountedUntil()
        if (from == null) {
            // Первый запуск: до этого момента учёта не было вовсе, и «провалом» это считать нельзя.
            watermarkRepository.setAccountedUntil(now)
            return null
        }
        val gap = Duration.between(from, now)
        if (gap < MIN_GAP) {
            watermarkRepository.setAccountedUntil(now)
            return null
        }
        if (!usageEventSource.isAvailable()) {
            Timber.tag(TAG).d("Провал %d мин, но доступа к системной статистике нет", gap.toMinutes())
            watermarkRepository.setAccountedUntil(now)
            return null
        }

        val intervals = foldUsageEvents(usageEventSource.events(from, now), from, now)
        val secondsByDate = mutableMapOf<LocalDate, Int>()
        for (interval in intervals) {
            record(interval, secondsByDate)
        }
        watermarkRepository.setAccountedUntil(now)

        val total = secondsByDate.values.sum()
        Timber.tag(TAG).d(
            "Досчитан провал %s..%s (%d мин): записано %d сек по %d отрезкам",
            from, now, gap.toMinutes(), total, intervals.size
        )
        return if (total > 0) Result(from, now, secondsByDate) else null
    }

    /**
     * Пишет один отрезок теми же методами, что и живой тик.
     *
     * Отрезок режется на куски по [CHUNK_SECONDS]: состояние лимита читается перед каждым куском,
     * поэтому переход «бюджет исчерпан → перерасход» происходит там же, где произошёл бы при живом
     * учёте, а не оптом в конце. Целиком записанный двухчасовой отрезок весь ушёл бы в ту корзину,
     * которая была актуальна на его начало.
     */
    private suspend fun record(interval: UsageInterval, secondsByDate: MutableMap<LocalDate, Int>) {
        val zone = ZoneId.systemDefault()
        for ((date, daySeconds) in splitByDate(interval, zone)) {
            var left = daySeconds
            while (left > 0) {
                val chunk = minOf(left, CHUNK_SECONDS)
                writeChunk(date, interval.packageName, chunk)
                secondsByDate[date] = (secondsByDate[date] ?: 0) + chunk
                left -= chunk
            }
        }
    }

    private suspend fun writeChunk(date: LocalDate, packageName: String, seconds: Int) {
        val targets = usageTickTargets(
            countsTowardsDailyLimit = countsTowardsDailyLimit(
                packageName = packageName,
                whitelist = policyRepository.whitelist.first(),
                alwaysAllowed = alwaysAllowedPackages.packages
            ),
            dailyLimitState = observeLimitState().first(),
            appLimitState = observeAppLimitState(packageName).first(),
            hasBonusAccessPass = packageName in observeBonusPasses().first()
        )
        when (targets.appBucket) {
            UsageBucket.BUDGET -> usageRepository.addAppScreenTime(date, packageName, seconds)
            UsageBucket.OVERRUN -> usageRepository.addAppOverrunTime(date, packageName, seconds)
        }
        when (targets.dailyBucket) {
            UsageBucket.BUDGET -> usageRepository.addScreenTime(date, seconds)
            UsageBucket.OVERRUN -> usageRepository.addOverrunTime(date, seconds)
            null -> Unit // «Всегда доступные», лаунчер, само KidGuard — дневной лимит не трогают
        }
        // Досчёт обязан списывать пропуск наравне с живым тиком: иначе время, проведённое в
        // приложении мимо мёртвого контроля, досталось бы ребёнку бесплатно.
        if (targets.spendsBonusWindow) {
            usageRepository.addAppBonusSpentTime(date, packageName, seconds)
        }
    }

    private companion object {
        const val TAG = "KidGuardBackfill"

        /**
         * Меньший разрыв досчитывать не стоит: он набегает от обычных пауз движка (перезапуск
         * сервиса, задержка планировщика), и системная статистика тут ничего не уточнит.
         */
        val MIN_GAP: Duration = Duration.ofMinutes(3)

        /** Шаг, с которым перечитывается состояние лимита при записи отрезка. */
        const val CHUNK_SECONDS = 60
    }
}
