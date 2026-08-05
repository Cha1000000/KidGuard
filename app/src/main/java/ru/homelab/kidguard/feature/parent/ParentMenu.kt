package ru.homelab.kidguard.feature.parent

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ru.homelab.kidguard.R

/**
 * Меню «три точки» родительского режима — одинаковое на всех трёх вкладках (Дети / Правила /
 * Статистика), см. [ScreenTitle][ru.homelab.kidguard.core.ui.components.ScreenTitle]. Устроено
 * по образцу `ChildMenu` из детского режима (TodayScreen).
 */
@Composable
fun ParentMenu(onOpenAbout: () -> Unit, onOpenAccount: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.parent_menu_open_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.parent_menu_about)) },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenAbout()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.parent_menu_account)) },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenAccount()
                }
            )
        }
    }
}
