package ru.homelab.kidguard.feature.parent.notifications

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.ChildAlertRow
import ru.homelab.kidguard.core.ui.components.ChildAvatars
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.GlassToggle

/**
 * Экран «Оповещения»: адрес для писем (один на родителя) и каналы по каждому ребёнку.
 *
 * Две секции разной природы. Верхняя — про самого родителя, от выбора ребёнка не зависит вовсе;
 * нижняя — список всех детей сразу, без переключателя активного (см. NotificationsViewModel).
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        CompactTopBar(title = stringResource(R.string.notifications_title), onBack = onBack)

        when {
            uiState.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            uiState.loadFailed -> LoadError(onRetry = viewModel::load)

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                SectionHeader(R.string.notifications_section_email)
                EmailSection(
                    state = uiState,
                    onEmailChanged = viewModel::onEmailChanged,
                    onClear = viewModel::clearEmail,
                    onSave = viewModel::saveEmail
                )

                SectionHeader(R.string.notifications_section_children)
                if (uiState.children.isEmpty()) {
                    Hint(stringResource(R.string.notifications_empty))
                } else {
                    uiState.children.forEach { row ->
                        ChildChannelsCard(
                            row = row,
                            onPushChange = { viewModel.setPush(row.child.id, it) },
                            onEmailChange = { viewModel.setEmail(row.child.id, it) }
                        )
                    }
                    Hint(
                        text = stringResource(R.string.notifications_warning),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                if (uiState.saveFailed) {
                    ErrorText(stringResource(R.string.notifications_save_error))
                }

                // Нижний отступ: под экраном плавает Dock Bar родительского каркаса.
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun ColumnScope.EmailSection(
    state: NotificationsUiState,
    onEmailChanged: (String) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit
) {
    OutlinedTextField(
        value = state.emailInput,
        onValueChange = onEmailChanged,
        label = { Text(stringResource(R.string.notifications_email_label)) },
        singleLine = true,
        isError = state.emailInvalid,
        enabled = !state.savingEmail,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done
        ),
        trailingIcon = {
            if (state.emailInput.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.notifications_email_clear_cd)
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    when {
        state.emailInvalid -> ErrorText(stringResource(R.string.notifications_email_invalid))
        state.showAccountHint -> Hint(
            stringResource(R.string.notifications_email_account_hint, state.accountEmail)
        )
    }

    when (val feedback = state.emailFeedback) {
        is EmailFeedback.VerificationSent ->
            Hint(stringResource(R.string.notifications_email_verification_sent, feedback.email))
        EmailFeedback.VerificationFailed ->
            ErrorText(stringResource(R.string.notifications_email_verification_failed))
        EmailFeedback.ResetToAccount ->
            Hint(stringResource(R.string.notifications_email_reset_done))
        null -> Unit
    }

    if (state.emailChannelUnused) {
        Hint(stringResource(R.string.notifications_email_unused))
    }

    Button(
        onClick = onSave,
        enabled = state.canSaveEmail,
        modifier = Modifier
            .align(Alignment.End)
            .padding(top = 8.dp)
    ) {
        if (state.savingEmail) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.notifications_email_save))
        }
    }
}

@Composable
private fun ChildChannelsCard(
    row: ChildAlertRow,
    onPushChange: (Boolean) -> Unit,
    onEmailChange: (Boolean) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(ChildAvatars.resFor(row.child.avatar)),
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = row.child.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            ChannelRow(
                labelRes = R.string.notifications_channel_push,
                checked = row.prefs.push,
                onCheckedChange = onPushChange
            )
            ChannelRow(
                labelRes = R.string.notifications_channel_email,
                checked = row.prefs.email,
                onCheckedChange = onEmailChange
            )
        }
    }
}

@Composable
private fun ChannelRow(
    @StringRes labelRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
        GlassToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LoadError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.notifications_load_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.notifications_retry))
        }
    }
}

@Composable
private fun SectionHeader(@StringRes text: Int) {
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
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 8.dp)
    )
}
