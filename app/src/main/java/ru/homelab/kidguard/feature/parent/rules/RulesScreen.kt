package ru.homelab.kidguard.feature.parent.rules

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.CenteredMessage

/** Вкладка «Правила» родительского режима (заглушка; наполнение — вехи 2–3). */
@Composable
fun RulesScreen(modifier: Modifier = Modifier) {
    CenteredMessage(stringResource(R.string.parent_rules_placeholder), modifier)
}
