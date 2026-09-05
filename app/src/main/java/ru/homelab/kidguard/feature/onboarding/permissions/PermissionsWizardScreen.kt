package ru.homelab.kidguard.feature.onboarding.permissions

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.DevicePermission
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassBackground
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.GlassDialog
import ru.homelab.kidguard.core.ui.components.InfoActionCard
import ru.homelab.kidguard.core.ui.components.LinearStepIndicator
import ru.homelab.kidguard.core.ui.components.descRes
import ru.homelab.kidguard.core.ui.components.isRequired
import ru.homelab.kidguard.core.ui.components.titleRes

/**
 * Мастер выдачи разрешений детского режима. По каждому разрешению показывает статус и кнопку
 * «Выдать», ведущую в системный экран. Статусы перепроверяются при каждом возврате на экран.
 */
@Composable
fun PermissionsWizardScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    finishLabelRes: Int = R.string.permissions_continue,
    viewModel: PermissionsViewModel = hiltViewModel()
) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    var showMissingRequiredWarning by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(StartActivityForResult()) {
        viewModel.refresh()
    }

    // CALL_PHONE — единственное runtime-разрешение в проекте: его выдаёт системный диалог, а не
    // экран настроек, поэтому у него отдельный контракт.
    val callPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) {
        viewModel.refresh()
    }

    // Перепроверяем статусы при каждом возврате на экран (в т.ч. из системных настроек).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val grantedCount = DevicePermission.entries.count { statuses[it] == true }
    val totalCount = DevicePermission.entries.size
    val missingRequired = DevicePermission.entries.filter { it.isRequired && statuses[it] != true }

    GlassBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // «Назад» есть только при входе из детского меню: в онбординге возвращаться некуда.
            if (onBack != null) {
                CompactTopBar(
                    title = stringResource(R.string.permissions_title),
                    onBack = onBack
                )
            }
            LazyColumn(
                // safeDrawingPadding — только когда нет CompactTopBar: он сам уже съедает статус-бар
                // через statusBarsPadding(), и добавление ещё и safeDrawingPadding ниже по дереву
                // (сиблинг, не потомок — инсет не считается «съеденным» автоматически) удваивало
                // отступ сверху между шапкой и первым текстом.
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (onBack == null) Modifier.safeDrawingPadding() else Modifier.navigationBarsPadding())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Заголовок в шапке уже есть — в списке он был бы вторым.
                if (onBack == null) {
                    item {
                        Text(
                            text = stringResource(R.string.permissions_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(R.string.permissions_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    item {
                        Text(
                            text = stringResource(R.string.permissions_subtitle),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                item {
                    Column {
                        Text(
                            text = stringResource(R.string.permissions_progress, grantedCount, totalCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        LinearStepIndicator(currentStep = grantedCount, totalSteps = totalCount)
                    }
                }
                item {
                    PermissionSectionHeader(R.string.permissions_section_required)
                }
                items(DevicePermission.entries.filter { it.isRequired }) { permission ->
                    PermissionRow(
                        permission = permission,
                        granted = statuses[permission] == true,
                        onGrant = {
                            if (permission == DevicePermission.EMERGENCY_CALL) {
                                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            } else {
                                viewModel.grantIntent(permission)?.let(launcher::launch)
                            }
                        }
                    )
                }
                item {
                    PermissionSectionHeader(R.string.permissions_section_optional)
                }
                items(DevicePermission.entries.filterNot { it.isRequired }) { permission ->
                    PermissionRow(
                        permission = permission,
                        granted = statuses[permission] == true,
                        onGrant = {
                            if (permission == DevicePermission.EMERGENCY_CALL) {
                                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            } else {
                                viewModel.grantIntent(permission)?.let(launcher::launch)
                            }
                        }
                    )
                }
                item {
                    AutostartCard(
                        onOpenSettings = { launcher.launch(viewModel.autostartIntent()) }
                    )
                }
                item {
                    RecentsLockCard()
                }
                item {
                    AlwaysOnVpnCard(
                        onOpenSettings = { launcher.launch(Intent(Settings.ACTION_VPN_SETTINGS)) }
                    )
                }
                item {
                    Button(
                        onClick = {
                            if (missingRequired.isEmpty()) onFinished() else showMissingRequiredWarning = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(finishLabelRes))
                    }
                }
            }
        }
    }

    if (showMissingRequiredWarning) {
        GlassDialog(
            onDismissRequest = { showMissingRequiredWarning = false },
            title = { Text(stringResource(R.string.permissions_warning_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.permissions_warning_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    missingRequired.forEach { permission ->
                        Text(
                            text = "• " + stringResource(permission.titleRes()),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.permissions_warning_question),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showMissingRequiredWarning = false
                    onFinished()
                }) {
                    Text(
                        text = stringResource(R.string.permissions_warning_proceed),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showMissingRequiredWarning = false }) {
                    Text(stringResource(R.string.permissions_warning_cancel))
                }
            }
        )
    }
}

@Composable
private fun PermissionSectionHeader(@StringRes textRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = modifier.padding(top = 4.dp, bottom = 2.dp, start = 4.dp)
    )
}

/**
 * Информационная карточка про вендорный автозапуск (веха 6В). Как и [AlwaysOnVpnCard], не входит в
 * список [DevicePermission]: на HiOS/MIUI/EMUI поверх стандартной оптимизации батареи есть свой
 * список автозапуска, который выгружает foreground-сервисы, но программного API ни для проверки,
 * ни для выдачи у вендоров нет — отсюда карточка-инструкция без статуса «выдано».
 */
@Composable
private fun AutostartCard(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    InfoActionCard(
        title = stringResource(R.string.autostart_title),
        description = stringResource(R.string.autostart_desc),
        actionLabel = stringResource(R.string.autostart_open_settings),
        onAction = onOpenSettings,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Замок карточки в списке последних приложений. Кнопки нет намеренно: системного экрана для этого
 * не существует ни у одного вендора, шаг делается жестом прямо в списке последних.
 *
 * Почему шаг вообще появился: разбор смертей контроля на боевом телефоне (05.09.2026) показал, что
 * все они — «остановлено пользователем», а в системном логе того же момента видно
 * `cleanType:oneKeyClean`, то есть кнопку «Очистить всё». Автозапуск при этом был давно разрешён —
 * то есть без этого шага мастер закрывал не ту дверь.
 */
@Composable
private fun RecentsLockCard(modifier: Modifier = Modifier) {
    InfoActionCard(
        title = stringResource(R.string.recents_lock_title),
        description = stringResource(R.string.recents_lock_desc),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Информационная карточка про always-on VPN (веха 5.4). Не входит в список [DevicePermission] —
 * это не проверяемое разрешение, а ручной шаг в системных настройках (без Device Owner его нельзя
 * форсить программно): включив always-on + «блокировать соединения без VPN», родитель защищает
 * блокировку от обхода простым отключением VPN.
 */
@Composable
private fun AlwaysOnVpnCard(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    InfoActionCard(
        title = stringResource(R.string.always_on_vpn_title),
        description = stringResource(R.string.always_on_vpn_desc),
        actionLabel = stringResource(R.string.always_on_vpn_open_settings),
        onAction = onOpenSettings,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun PermissionRow(
    permission: DevicePermission,
    granted: Boolean,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(permission.titleRes()),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(permission.descRes()),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Бейдж — в правой колонке над кнопкой, а не в тексте слева: так «статус» и «действие»
            // читаются одним блоком («обязательно → выдать»), а левая колонка остаётся чистым
            // текстом (заголовок+описание) без постороннего элемента между ними.
            Column(horizontalAlignment = Alignment.End) {
                if (permission.isRequired && !granted) {
                    Text(
                        text = stringResource(R.string.permissions_required_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (granted) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.permissions_granted),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    OutlinedButton(onClick = onGrant) {
                        Text(stringResource(R.string.permissions_grant))
                    }
                }
            }
        }
    }
}

