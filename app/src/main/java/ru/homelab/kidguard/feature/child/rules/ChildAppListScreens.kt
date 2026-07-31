package ru.homelab.kidguard.feature.child.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.AppIconImage
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.EmptyState
import ru.homelab.kidguard.core.ui.components.GlassCard

/** Отступ от компактного топбара до контента — общий для всех детских экранов правил. */
internal val TopBarContentGap = 16.dp

/**
 * Детские экраны «Запрещено» и «Доступно» (раскрытие карточек сетки 2×2 на «Сегодня»,
 * Фаза UI-аудита) — простые read-only списки приложений без цифр и действий. Живут в одном
 * файле: верстка идентична, различаются только источник данных и подписи пустого состояния.
 */
@Composable
fun ChildBlockedAppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChildBlockedAppsViewModel = hiltViewModel()
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        CompactTopBar(
            title = stringResource(R.string.child_blocked_title),
            onBack = onBack
        )
        Spacer(Modifier.height(TopBarContentGap))
        ChildAppListContent(
            apps = apps,
            emptyIcon = ImageVector.vectorResource(R.drawable.ic_block),
            emptyTitle = stringResource(R.string.child_blocked_empty_title),
            emptyDescription = stringResource(R.string.child_blocked_empty_desc)
        )
    }
}

@Composable
fun ChildAllowedAppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChildAllowedAppsViewModel = hiltViewModel()
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        CompactTopBar(
            title = stringResource(R.string.child_allowed_title),
            onBack = onBack
        )
        Spacer(Modifier.height(TopBarContentGap))
        ChildAppListContent(
            apps = apps,
            emptyIcon = Icons.Filled.CheckCircle,
            emptyTitle = stringResource(R.string.child_allowed_empty_title),
            emptyDescription = stringResource(R.string.child_allowed_empty_desc)
        )
    }
}

@Composable
private fun ChildAppListContent(
    apps: List<ChildAppUi>?,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyDescription: String
) {
    when {
        apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        apps.isEmpty() -> EmptyState(
            icon = emptyIcon,
            title = emptyTitle,
            description = emptyDescription,
            modifier = Modifier.fillMaxSize()
        )

        else -> LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                ChildAppRow(app)
            }
        }
    }
}

@Composable
private fun ChildAppRow(app: ChildAppUi) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconImage(icon = app.icon, label = app.label, packageName = app.packageName)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
