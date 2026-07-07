package ru.homelab.kidguard.feature.parent.statistics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.CenteredMessage
import ru.homelab.kidguard.core.ui.components.ScreenTitle

/** Вкладка «Статистика» родительского режима (заглушка; наполнение — веха 4). */
@Composable
fun StatisticsScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.parent_tab_statistics))
        CenteredMessage(
            text = stringResource(R.string.parent_statistics_placeholder),
            modifier = Modifier.weight(1f)
        )
    }
}
