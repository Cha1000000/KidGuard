package ru.homelab.kidguard.feature.parent.children

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import ru.homelab.kidguard.R
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * «Сколько назад» для плашки и листа watchdog: «6 мин», «14 ч», «3 дн».
 *
 * Округляем вниз до крупной единицы — родителю нужен порядок величины, а не точность до секунды.
 * Меньше минуты показываем как «1 мин»: «0 мин» выглядело бы поломкой.
 */
@Composable
fun formatAgo(from: Instant, now: Instant): String {
    val minutes = Duration.between(from, now).toMinutes().coerceAtLeast(1)
    return when {
        minutes < 60 -> stringResource(R.string.duration_minutes, minutes.toInt())
        minutes < 60 * 24 -> stringResource(R.string.duration_hours, (minutes / 60).toInt())
        else -> stringResource(R.string.duration_days, (minutes / (60 * 24)).toInt())
    }
}

/**
 * «Сейчас» для плашки и листа watchdog, которое идёт само.
 *
 * Раньше было `remember(child) { Instant.now() }`: время бралось один раз и пересчитывалось только
 * при смене данных ребёнка. Но молчащий телефон данных как раз не меняет — сервер отдаёт тот же
 * `lastSeenAt` — поэтому «сейчас» замирало, и плашка «не выходил на связь» не появлялась ни на
 * открытом экране, ни после обновления свайпом (найдено 17.09.2026: тревога о молчании Олега
 * родителю не показалась).
 *
 * [keys] перезапускают отсчёт немедленно — при новых данных ребёнка «сейчас» свежее сразу, а не
 * через [PERIOD_MS].
 */
@Composable
fun rememberTickingNow(vararg keys: Any?): Instant {
    val now by produceState(initialValue = Instant.now(), *keys) {
        value = Instant.now()
        while (true) {
            delay(PERIOD_MS)
            value = Instant.now()
        }
    }
    return now
}

/** Шаг «сейчас»: порог молчания 40 минут, плашка с точностью до минуты — полминуты с запасом. */
private const val PERIOD_MS = 30_000L
