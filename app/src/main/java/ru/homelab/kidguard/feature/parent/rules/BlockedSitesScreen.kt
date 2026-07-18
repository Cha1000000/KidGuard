package ru.homelab.kidguard.feature.parent.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import ru.homelab.kidguard.core.ui.components.GlassDockBarReservedHeight
import ru.homelab.kidguard.core.ui.components.GlassToggle

/** Экран «Запрет сайтов» (веха 4.1.2): DNS-чёрный список доменов + тумблер блокировки google-поиска. */
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
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
                    supportingText = {
                        if (inputError) {
                            Text(stringResource(R.string.blocked_sites_invalid))
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        viewModel.addSite(input)
                        if (input.isNotBlank()) input = ""
                    }
                ) {
                    Text(stringResource(R.string.blocked_sites_add))
                }
            }

            if (sites.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.blocked_sites_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(top = 12.dp),
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

            Text(
                text = stringResource(R.string.blocked_sites_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = GlassDockBarReservedHeight)
            )
        }
    }
}

@Composable
private fun BlockedSiteRow(
    site: BlockedSite,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
