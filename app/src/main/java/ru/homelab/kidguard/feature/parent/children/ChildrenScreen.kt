package ru.homelab.kidguard.feature.parent.children

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.CenteredMessage
import ru.homelab.kidguard.core.ui.components.ScreenTitle

/** Вкладка «Дети» родительского режима (заглушка; наполнение — веха 4). */
@Composable
fun ChildrenScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.parent_tab_children))
        CenteredMessage(
            text = stringResource(R.string.parent_children_placeholder),
            modifier = Modifier.weight(1f)
        )
    }
}
