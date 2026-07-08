package ru.homelab.kidguard.feature.parent.rules

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppLimitsViewModel = hiltViewModel()
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AppLimitUi?>(null) }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rules_app_limits_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.app_limits_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.app_limits_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppLimitRow(app = app, onClick = { editing = app })
                }
            }
        }
    }

    editing?.let { app ->
        AppLimitEditorSheet(
            app = app,
            onDismiss = { editing = null },
            onSave = { minutes ->
                viewModel.setAppLimit(app.packageName, minutes)
                editing = null
            }
        )
    }
}

@Composable
private fun AppLimitRow(app: AppLimitUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
            if (app.limitMinutes != null) {
                Text(
                    text = stringResource(R.string.app_limits_spent, app.spentMinutes, app.limitMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = app.limitMinutes?.let { formatLimit(it) }
                ?: stringResource(R.string.app_limits_no_limit),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (app.limitMinutes != null) FontWeight.Bold else FontWeight.Normal,
            color = if (app.limitMinutes != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLimitEditorSheet(
    app: AppLimitUi,
    onDismiss: () -> Unit,
    onSave: (minutes: Int?) -> Unit
) {
    var minutes by remember { mutableIntStateOf(app.limitMinutes ?: DEFAULT_MINUTES) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                )
                Text(app.label, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = stringResource(R.string.app_limits_sheet_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
            )
            Text(
                text = formatLimit(minutes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            Slider(
                value = minutes.toFloat(),
                onValueChange = { minutes = (it / STEP_MINUTES).toInt() * STEP_MINUTES },
                valueRange = STEP_MINUTES.toFloat()..MAX_MINUTES.toFloat(),
                steps = MAX_MINUTES / STEP_MINUTES - 2
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = { onSave(null) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.app_limits_remove))
                }
                Button(
                    onClick = { onSave(minutes) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.app_limits_save))
                }
            }
        }
    }
}

@Composable
private fun formatLimit(minutes: Int): String = when {
    minutes >= 60 -> stringResource(R.string.limit_value_hm, minutes / 60, minutes % 60)
    else -> stringResource(R.string.limit_value_m, minutes)
}

private const val DEFAULT_MINUTES = 60
private const val MAX_MINUTES = 300
private const val STEP_MINUTES = 15
