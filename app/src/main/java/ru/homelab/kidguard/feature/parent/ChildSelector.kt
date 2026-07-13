package ru.homelab.kidguard.feature.parent

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.ui.components.ChildAvatars

/**
 * Чип активного ребёнка (веха 4.5): аватарка + имя, по тапу — dropdown-меню с профилями
 * детей. При одном ребёнке чип статичный (без стрелки и меню). Выбор общий для всего
 * приложения родителя — «Правила» и «Статистика» всегда показывают одного ребёнка.
 */
@Composable
fun ChildSelectorChip(
    modifier: Modifier = Modifier,
    viewModel: ChildSelectorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Обновляем при каждом входе на вкладку: ребёнка могли добавить или переключить на другой вкладке.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Ошибка переключения (нет сети) — короткое сообщение, остаёмся на текущем ребёнке.
    if (uiState.switchError) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, R.string.child_selector_switch_error, Toast.LENGTH_SHORT).show()
            viewModel.dismissError()
        }
    }

    val active = uiState.active ?: return

    Box(modifier = modifier.padding(start = 16.dp, bottom = 8.dp)) {
        Surface(
            onClick = { if (uiState.selectable) menuExpanded = true },
            enabled = uiState.selectable,
            shape = CircleShape,
            color = if (uiState.selectable) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surface
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = 5.dp,
                    end = if (uiState.selectable) 8.dp else 12.dp,
                    top = 5.dp,
                    bottom = 5.dp
                )
            ) {
                Image(
                    painter = painterResource(ChildAvatars.resFor(active.avatar)),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = active.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 9.dp)
                )
                if (uiState.selectable) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            uiState.children.forEach { child ->
                ChildMenuItem(
                    child = child,
                    isActive = child.id == active.id,
                    onClick = {
                        menuExpanded = false
                        viewModel.select(child)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChildMenuItem(child: Child, isActive: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        onClick = onClick,
        leadingIcon = {
            Image(
                painter = painterResource(ChildAvatars.resFor(child.avatar)),
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
        },
        text = {
            Text(
                text = child.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        },
        trailingIcon = if (isActive) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            null
        }
    )
}
