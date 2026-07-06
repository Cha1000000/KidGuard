package ru.homelab.kidguard.feature.parent.children

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.CenteredMessage

/** Вкладка «Дети» родительского режима (заглушка; наполнение — веха 4). */
@Composable
fun ChildrenScreen(modifier: Modifier = Modifier) {
    CenteredMessage(stringResource(R.string.parent_children_placeholder), modifier)
}
