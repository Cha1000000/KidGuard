package ru.homelab.kidguard.feature.parent.children

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.ui.components.AvatarGrid
import ru.homelab.kidguard.core.ui.components.ChildAvatars
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.GlassDockBarReservedHeight
import ru.homelab.kidguard.core.ui.components.ScreenTitle

/** Открытый bottom-sheet на экране «Дети». */
private sealed interface ChildrenSheet {
    data object AddChild : ChildrenSheet
    data class Actions(val child: Child) : ChildrenSheet
    data class Code(val childName: String, val code: String) : ChildrenSheet
    data class CoParent(val child: Child) : ChildrenSheet
    data class Edit(val child: Child) : ChildrenSheet
    data class ConfirmDelete(val child: Child) : ChildrenSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildrenScreen(
    modifier: Modifier = Modifier,
    viewModel: ChildrenViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<ChildrenSheet?>(null) }

    // Обновляем при каждом входе на вкладку: VM переживает переключение вкладок, а статус
    // ребёнка мог измениться снаружи (устройство ввело pairing-код → «Привязан»).
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.parent_tab_children))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // Резерв снизу — плавающий GlassDockBar лежит поверх этого экрана, не должен
            // закрывать кнопку «Добавить ребёнка».
            contentPadding = PaddingValues(bottom = GlassDockBarReservedHeight)
        ) {
            items(uiState.children, key = { it.id }) { child ->
                ChildCard(child = child, onClick = { sheet = ChildrenSheet.Actions(child) })
            }
            item {
                AddChildButton(onClick = { sheet = ChildrenSheet.AddChild })
            }
            if (uiState.children.isEmpty() && !uiState.loading) {
                item {
                    Text(
                        text = stringResource(
                            if (uiState.loadError) R.string.children_load_error else R.string.children_empty
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
                    )
                }
            }
        }
    }

    when (val current = sheet) {
        null -> Unit

        ChildrenSheet.AddChild -> AddChildSheet(
            onDismiss = { sheet = null },
            onCreate = { name, avatar ->
                viewModel.createChild(
                    name = name,
                    avatar = avatar,
                    onCode = { code -> sheet = ChildrenSheet.Code(name, code) },
                    onError = { sheet = null }
                )
            }
        )

        is ChildrenSheet.Actions -> ChildActionsSheet(
            child = current.child,
            onDismiss = { sheet = null },
            onShowCode = {
                viewModel.regenerateCode(
                    childId = current.child.id,
                    onCode = { code -> sheet = ChildrenSheet.Code(current.child.name, code) },
                    onError = { sheet = null }
                )
            },
            onInviteCoParent = { sheet = ChildrenSheet.CoParent(current.child) },
            onEdit = { sheet = ChildrenSheet.Edit(current.child) },
            onDelete = { sheet = ChildrenSheet.ConfirmDelete(current.child) }
        )

        is ChildrenSheet.Code -> CodeSheet(
            code = current.code,
            onDismiss = { sheet = null }
        )

        is ChildrenSheet.CoParent -> CoParentSheet(
            onDismiss = { sheet = null },
            onInvite = { email, onResult ->
                viewModel.inviteCoParent(current.child.id, email) { result -> onResult(result) }
            }
        )

        is ChildrenSheet.Edit -> EditChildSheet(
            child = current.child,
            onDismiss = { sheet = null },
            onSave = { name, avatar, onError ->
                viewModel.updateChild(
                    childId = current.child.id,
                    name = name,
                    avatar = avatar,
                    onDone = {},
                    onError = onError
                )
            }
        )

        is ChildrenSheet.ConfirmDelete -> DeleteChildDialog(
            child = current.child,
            onDismiss = { sheet = null },
            onConfirm = { onError ->
                viewModel.deleteChild(
                    childId = current.child.id,
                    onDone = {},
                    onError = onError
                )
            }
        )
    }
}

@Composable
private fun ChildCard(child: Child, onClick: () -> Unit) {
    GlassCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(ChildAvatars.resFor(child.avatar)),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(child.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        if (child.paired) R.string.children_status_paired else R.string.children_status_waiting
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (child.paired) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun AddChildButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.children_add),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChildSheet(onDismiss: () -> Unit, onCreate: (name: String, avatar: Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildActionsSheet(
    child: Child,
    onDismiss: () -> Unit,
    onShowCode: () -> Unit,
    onInviteCoParent: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
            ActionButton(
                icon = Icons.Filled.Delete,
                label = stringResource(R.string.child_delete),
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
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
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke = ButtonDefaults.outlinedButtonBorder(enabled = true)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeSheet(code: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.child_code_copied)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.child_code_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.child_code_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formatCode(code),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Text(
                text = stringResource(R.string.child_code_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp)
            )
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(stringResource(R.string.child_code_copy))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoParentSheet(
    onDismiss: () -> Unit,
    onInvite: (email: String, onResult: (CoParentResult) -> Unit) -> Unit
) {
    var email by remember { mutableStateOf("") }
    val context = LocalContext.current
    val linkedMsg = stringResource(R.string.coparent_linked)
    val pendingMsg = stringResource(R.string.coparent_pending)
    val errorMsg = stringResource(R.string.common_error)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.coparent_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.coparent_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.coparent_email_label)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    onInvite(email) { result ->
                        val msg = when (result) {
                            CoParentResult.LINKED -> linkedMsg
                            CoParentResult.PENDING -> pendingMsg
                            CoParentResult.ERROR -> errorMsg
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                    onDismiss()
                },
                enabled = isValidEmail(email),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp)
            ) {
                Text(stringResource(R.string.coparent_invite))
            }
        }
    }
}

private fun isValidEmail(email: String): Boolean =
    email.contains("@") && email.contains(".") && email.trim().length >= 5

/** «482915» -> «482 915» для читаемости. */
private fun formatCode(code: String): String =
    if (code.length == 6) "${code.substring(0, 3)} ${code.substring(3)}" else code

/** Sheet редактирования профиля — структура как у [AddChildSheet], но с предзаполнением. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditChildSheet(
    child: Child,
    onDismiss: () -> Unit,
    onSave: (name: String, avatar: Int, onError: () -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(child.name) }
    var avatar by remember { mutableStateOf(child.avatar) }
    val context = LocalContext.current
    val errorMsg = stringResource(R.string.common_error)

    ModalBottomSheet(onDismissRequest = onDismiss) {
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
private fun DeleteChildDialog(
    child: Child,
    onDismiss: () -> Unit,
    onConfirm: (onError: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val errorMsg = stringResource(R.string.common_error)

    AlertDialog(
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
