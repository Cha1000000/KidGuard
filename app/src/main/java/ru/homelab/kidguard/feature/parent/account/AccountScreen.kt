package ru.homelab.kidguard.feature.parent.account

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.GlassDangerButton
import ru.homelab.kidguard.core.ui.components.GlassDialog

/** Открытый диалог на экране «Аккаунт». */
private sealed interface AccountDialog {
    data object SignOut : AccountDialog
    data object Delete : AccountDialog
}

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        CompactTopBar(title = stringResource(R.string.account_title), onBack = onBack)

        val state = uiState
        if (state == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountAvatar(name = state.displayName)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(text = state.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = state.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AccountSectionHeader(R.string.account_section_session)
            OutlinedButton(
                onClick = { dialog = AccountDialog.SignOut },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_logout),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = stringResource(R.string.account_sign_out), modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                text = stringResource(R.string.account_sign_out_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            AccountSectionHeader(R.string.account_section_danger)
            GlassDangerButton(
                onClick = { dialog = AccountDialog.Delete },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = stringResource(R.string.account_delete), modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                text = stringResource(R.string.account_delete_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        when (dialog) {
            AccountDialog.SignOut -> SignOutDialog(
                state = state,
                onDismiss = { dialog = null },
                onConfirm = { viewModel.signOut(onDone = onSignedOut) }
            )

            AccountDialog.Delete -> DeleteAccountDialog(
                state = state,
                onDismiss = { dialog = null },
                onConfirm = { viewModel.deleteAccount(onDone = onSignedOut) }
            )

            null -> Unit
        }
    }
}

@Composable
private fun AccountAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifBlank { "?" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/** Заголовок секции — как `RuleSectionHeader` в RulesScreen (uppercase, titleSmall, bold). */
@Composable
private fun AccountSectionHeader(@StringRes text: Int) {
    Text(
        text = stringResource(text).uppercase(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SignOutDialog(
    state: AccountUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    GlassDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text(stringResource(R.string.account_signout_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.account_signout_dialog_text))
                if (state.errorRes != null) {
                    Text(
                        text = stringResource(state.errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                // Отдельной короткой строки под текст кнопки диалога в задании нет — переиспользуем
                // account_sign_out («Выйти из аккаунта»), кнопка достаточно широкая, чтобы не переноситься.
                Button(onClick = onConfirm) {
                    Text(stringResource(R.string.account_sign_out))
                }
            }
        },
        dismissButton = if (state.busy) null else {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
        }
    )
}

@Composable
private fun DeleteAccountDialog(
    state: AccountUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var confirmText by remember { mutableStateOf("") }
    val requiredWord = stringResource(R.string.account_delete_confirm_word)
    val canConfirm = confirmText.trim() == requiredWord

    GlassDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.account_delete_dialog_title),
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                // Детей с со-родителем сервер не удаляет, а только отвязывает от нас, поэтому
                // в перечень «будет удалено» они попадать не должны — про них отдельный абзац ниже.
                val (kept, deleted) = state.children.partition { it.keptByCoParent }

                Text(stringResource(R.string.account_delete_dialog_intro))
                Text(
                    text = "• " + stringResource(R.string.account_delete_dialog_account, state.email),
                    modifier = Modifier.padding(top = 8.dp)
                )
                deleted.forEach { child ->
                    Text(
                        text = "• " + stringResource(R.string.account_delete_dialog_child, child.name),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (kept.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.account_delete_dialog_kept,
                            kept.joinToString(", ") { it.name }
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.account_delete_dialog_confirm_prompt, requiredWord),
                    modifier = Modifier.padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (state.errorRes != null) {
                    Text(
                        text = stringResource(state.errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Button(
                    onClick = onConfirm,
                    enabled = canConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        },
        dismissButton = if (state.busy) null else {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
        }
    )
}
