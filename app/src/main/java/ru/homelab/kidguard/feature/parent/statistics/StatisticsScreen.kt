package ru.homelab.kidguard.feature.parent.statistics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.CenteredMessage

/** Вкладка «Статистика» родительского режима (заглушка; наполнение — веха 4). */
@Composable
fun StatisticsScreen(modifier: Modifier = Modifier) {
    CenteredMessage(stringResource(R.string.parent_statistics_placeholder), modifier)
}
