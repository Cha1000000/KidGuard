package ru.homelab.kidguard.feature.parent.children

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.ui.components.AvatarGrid
import ru.homelab.kidguard.core.ui.components.GlassBottomSheet
import ru.homelab.kidguard.core.ui.components.GlassDialog

/** Модалки создания, редактирования и удаления профиля ребёнка. */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddChildSheet(onDismiss: () -> Unit, onCreate: (name: String, avatar: Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(0) }

    GlassBottomSheet(onDismissRequest = onDismiss) {
        // verticalScroll + ручная сетка рядами (не Lazy) — чтобы всё содержимое, включая кнопку
        // «Создать», было доступно скроллом на любом экране; LazyVerticalGrid внутри скролла
        // конфликтует.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(stringResource(R.string.add_child_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.add_child_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
            Text(
                text = stringResource(R.string.add_child_avatar_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)
            )
            AvatarGrid(
                selected = avatar,
                onSelect = { avatar = it },
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Button(
                onClick = { onCreate(name, avatar) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp)
            ) {
                Text(stringResource(R.string.add_child_create))
            }
        }
    }
}

/** Sheet редактирования профиля — структура как у [AddChildSheet], но с предзаполнением. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditChildSheet(
    child: Child,
    onDismiss: () -> Unit,
    onSave: (name: String, avatar: Int, onError: () -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(child.name) }
    var avatar by remember { mutableStateOf(child.avatar) }
    val context = LocalContext.current
    val errorMsg = stringResource(R.string.common_error)

    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(stringResource(R.string.edit_child_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.add_child_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
            Text(
                text = stringResource(R.string.add_child_avatar_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)
            )
            AvatarGrid(
                selected = avatar,
                onSelect = { avatar = it },
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Button(
                onClick = {
                    onSave(name, avatar) { Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show() }
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp)
            ) {
                Text(stringResource(R.string.edit_child_save))
            }
        }
    }
}

/** Диалог подтверждения удаления ребёнка — необратимо стирает правила и статистику. */
@Composable
internal fun DeleteChildDialog(
    child: Child,
    onDismiss: () -> Unit,
    onConfirm: (onError: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val errorMsg = stringResource(R.string.common_error)

    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_child_title, child.name)) },
        text = { Text(stringResource(R.string.delete_child_message)) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm { Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show() }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.delete_child_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
