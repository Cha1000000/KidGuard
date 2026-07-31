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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.AppIconImage
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.EmptyState
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.formatDurationMinutes

/**
 * Детский экран «Лимиты» (раскрытие карточки сетки 2×2 на «Сегодня», Фаза UI-аудита): список
 * приложений с дневным лимитом времени — сколько уже потрачено сегодня и сам лимит. Только для
 * просмотра — изменить лимит может лишь родитель через PIN-защищённые экраны «Правила».
 */
@Composable
fun ChildLimitedAppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChildLimitedAppsViewModel = hiltViewModel()
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        CompactTopBar(
            title = stringResource(R.string.child_limits_title),
            onBack = onBack
        )
        Spacer(Modifier.height(TopBarContentGap))
        when {
            apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            apps.orEmpty().isEmpty() -> EmptyState(
                icon = ImageVector.vectorResource(R.drawable.ic_timer),
                title = stringResource(R.string.child_limits_empty_title),
                description = stringResource(R.string.child_limits_empty_desc),
                modifier = Modifier.fillMaxSize()
            )

            else -> LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(apps.orEmpty(), key = { it.packageName }) { app ->
                    ChildLimitedAppRow(app)
                }
            }
        }
    }
}

@Composable
private fun ChildLimitedAppRow(app: ChildLimitedAppUi) {
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
                    text = if (app.leftMinutes <= 0) {
                        stringResource(R.string.child_limits_expired)
                    } else {
                        stringResource(
                            R.string.child_limits_spent,
                            formatDurationMinutes(app.spentMinutes),
                            formatDurationMinutes(app.limitMinutes)
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatDurationMinutes(app.limitMinutes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
