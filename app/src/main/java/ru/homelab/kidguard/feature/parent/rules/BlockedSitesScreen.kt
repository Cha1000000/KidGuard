package ru.homelab.kidguard.feature.parent.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.BlockedSite
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.GlassToggle

/** Цвет предупреждения (жёлтый треугольник), единый с пометками «критично» в пикерах. */
private val WarningColor = Color(0xFFF5B301)

/** Высота, к которой выравниваем поле ввода и кнопку «Добавить» (стандарт OutlinedTextField). */
private val InputRowHeight = 56.dp

/** Экран «Запрет сайтов»: DNS-чёрный список доменов + тумблер блокировки google-поиска. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedSitesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockedSitesViewModel = hiltViewModel()
) {
    val blockGoogleSearch by viewModel.blockGoogleSearch.collectAsStateWithLifecycle()
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val inputError by viewModel.inputError.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    // Плашка про lockdown актуальна только когда что-то реально блокируется.
    val showLockdownWarning = blockGoogleSearch || sites.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        CompactTopBar(
            title = stringResource(R.string.rules_blocked_sites_title),
            onBack = onBack
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.blocked_sites_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            if (showLockdownWarning) {
                LockdownWarning()
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.blocked_sites_google_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.blocked_sites_google_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    GlassToggle(
                        checked = blockGoogleSearch,
                        onCheckedChange = viewModel::setBlockGoogleSearch
                    )
                }
            }

            Text(
                text = stringResource(R.string.blocked_sites_list_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
            )

            // Поле и кнопка выровнены по высоте (общий InputRowHeight), центрированы — границы совпадают.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        viewModel.clearInputError()
                    },
                    placeholder = { Text(stringResource(R.string.blocked_sites_input_placeholder)) },
                    singleLine = true,
                    isError = inputError,
                    modifier = Modifier
                        .weight(1f)
                        .height(InputRowHeight)
                )
                Button(
                    onClick = {
                        viewModel.addSite(input)
                        if (input.isNotBlank()) input = ""
                    },
                    modifier = Modifier.height(InputRowHeight)
                ) {
                    Text(stringResource(R.string.blocked_sites_add))
                }
            }
            // Ошибку показываем отдельной строкой под рядом — чтобы поле не «прыгало» по высоте.
            if (inputError) {
                Text(
                    text = stringResource(R.string.blocked_sites_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            if (sites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.blocked_sites_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                // weight(1f) даёт списку ограниченную высоту → он прокручивается внутри себя,
                // а шапка (поле ввода, карточка) и подсказка снизу остаются на месте.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sites, key = { it.domain }) { site ->
                        BlockedSiteRow(
                            site = site,
                            onToggle = { enabled -> viewModel.setSiteEnabled(site.domain, enabled) },
                            onRemove = { viewModel.removeSite(site.domain) }
                        )
                    }
                }
            }

            // Подсказка прижата к низу (без резерва под док-бар — на детальном экране его нет,
            // а нижний отступ под жест-бар уже даёт safeDrawing у хоста). Список получает место.
            Text(
                text = stringResource(R.string.blocked_sites_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )
        }
    }
}

/** Плашка-предупреждение про системный lockdown («Блокировать соединения без VPN»). */
@Composable
private fun LockdownWarning() {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(shape)
            .background(WarningColor.copy(alpha = 0.12f))
            .border(1.dp, WarningColor.copy(alpha = 0.4f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = WarningColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(R.string.blocked_sites_lockdown_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BlockedSiteRow(
    site: BlockedSite,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    // Компактная карточка: внутренние отступы ~вдвое меньше стандартных, скругление меньше.
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(checked = site.enabled, onCheckedChange = onToggle)
            Text(
                text = site.domain,
                style = MaterialTheme.typography.bodyLarge,
                color = if (site.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textDecoration = if (site.enabled) TextDecoration.None else TextDecoration.LineThrough,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.blocked_sites_remove),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
