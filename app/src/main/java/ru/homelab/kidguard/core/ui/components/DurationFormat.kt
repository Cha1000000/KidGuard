package ru.homelab.kidguard.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.homelab.kidguard.R

/**
 * Длительность в минутах человеку: «2 ч 30 мин», «2 ч» (целые часы без «00 мин»), «45 мин».
 * Общая для детских экранов — раньше эта же логика лежала приватно в TodayScreen.
 */
@Composable
fun formatDurationMinutes(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    return when {
        // Целые часы показываем без «00 мин» — «2 ч» вместо «2 ч 00 мин» (как на макете).
        safe >= 60 && safe % 60 == 0 -> stringResource(R.string.limit_value_h, safe / 60)
        safe >= 60 -> stringResource(R.string.limit_value_hm, safe / 60, safe % 60)
        else -> stringResource(R.string.limit_value_m, safe)
    }
}
