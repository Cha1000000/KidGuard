package ru.homelab.kidguard.feature.parent.children

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.ui.components.ChildAvatars
import ru.homelab.kidguard.core.ui.components.GlassBottomSheet

/** Меню действий с ребёнком: код привязки, приглашение со-родителя, редактирование, удаление. */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChildActionsSheet(
    child: Child,
    onDismiss: () -> Unit,
    onShowCode: () -> Unit,
    onInviteCoParent: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(ChildAvatars.resFor(child.avatar)),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                )
                Text(child.name, style = MaterialTheme.typography.titleLarge)
            }
            if (!child.paired) {
                ActionButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_link),
                    label = stringResource(R.string.child_code_get),
                    onClick = onShowCode,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            ActionButton(
                icon = ImageVector.vectorResource(R.drawable.ic_person_add),
                label = stringResource(R.string.child_coparent),
                onClick = onInviteCoParent,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (child.paired) {
                // На случай переустановки приложения на телефоне ребёнка (или сброса
                // устройства) — новый код привязки погашает прежний, но НЕ трогает исходную
                // дату привязки и не затирает правила/статистику (см. pairingService.pairDevice
                // на сервере), поэтому пересоздавать ребёнка вручную не нужно.
                ActionButton(
                    icon = Icons.Filled.Refresh,
                    label = stringResource(R.string.child_code_new),
                    onClick = onShowCode,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            ActionButton(
                icon = Icons.Filled.Edit,
                label = stringResource(R.string.child_edit),
                onClick = onEdit,
                modifier = Modifier.padding(top = 12.dp)
            )
            // Красный «Удалить» намеренно НЕ завязан на MaterialTheme.colorScheme.error: в тёмной
            // теме M3-токен ошибки светлый розовый (#F2B8B5) и на тёмном фоне выглядит блёкло —
            // фиксируем цвет светлой темы для обеих (по просьбе Володи, светлая уже устраивала).
            val deleteColor = Color(0xFFB3261E)
            ActionButton(
                icon = Icons.Filled.Delete,
                label = stringResource(R.string.child_delete),
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = deleteColor),
                border = BorderStroke(1.dp, deleteColor.copy(alpha = 0.5f)),
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
            )
        }
    }
}

/** Кнопка действия в листе [ChildActionsSheet] — иконка + подпись, для наглядности и различимости. */
@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
) {
    OutlinedButton(
        onClick = onClick,
        colors = colors,
        border = border,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text = label, modifier = Modifier.padding(start = 10.dp))
    }
}
