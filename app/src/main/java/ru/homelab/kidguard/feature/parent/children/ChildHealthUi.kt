package ru.homelab.kidguard.feature.parent.children

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.homelab.kidguard.R
import java.time.Duration
import java.time.Instant

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
