package ru.homelab.kidguard.feature.onboarding.permissions

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.DevicePermission
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassBackground
import ru.homelab.kidguard.core.ui.components.GlassCard

/**
 * Мастер выдачи разрешений детского режима. По каждому разрешению показывает статус и кнопку
 * «Выдать», ведущую в системный экран. Статусы перепроверяются при каждом возврате на экран.
 */
@Composable
fun PermissionsWizardScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionsViewModel = hiltViewModel()
) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(StartActivityForResult()) {
        viewModel.refresh()
    }

    // Перепроверяем статусы при каждом возврате на экран (в т.ч. из системных настроек).
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
        items(DevicePermission.entries) { permission ->
            PermissionRow(
                permission = permission,
                granted = statuses[permission] == true,
                onGrant = { viewModel.grantIntent(permission)?.let(launcher::launch) }
            )
        }
        item {
            AlwaysOnVpnCard(
                onOpenSettings = { launcher.launch(Intent(Settings.ACTION_VPN_SETTINGS)) }
            )
        }
        item {
            Button(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.permissions_continue))
            }
        }
    }
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
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.always_on_vpn_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.always_on_vpn_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(stringResource(R.string.always_on_vpn_open_settings))
            }
        }
    }
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

@StringRes
private fun DevicePermission.titleRes(): Int = when (this) {
    DevicePermission.USAGE_ACCESS -> R.string.permission_usage_title
    DevicePermission.ACCESSIBILITY -> R.string.permission_accessibility_title
    DevicePermission.OVERLAY -> R.string.permission_overlay_title
    DevicePermission.DEVICE_ADMIN -> R.string.permission_device_admin_title
    DevicePermission.BATTERY_OPTIMIZATION -> R.string.permission_battery_title
    DevicePermission.NOTIFICATIONS -> R.string.permission_notifications_title
    DevicePermission.VPN -> R.string.permission_vpn_title
}

@StringRes
private fun DevicePermission.descRes(): Int = when (this) {
    DevicePermission.USAGE_ACCESS -> R.string.permission_usage_desc
    DevicePermission.ACCESSIBILITY -> R.string.permission_accessibility_desc
    DevicePermission.OVERLAY -> R.string.permission_overlay_desc
    DevicePermission.DEVICE_ADMIN -> R.string.permission_device_admin_desc
    DevicePermission.BATTERY_OPTIMIZATION -> R.string.permission_battery_desc
    DevicePermission.NOTIFICATIONS -> R.string.permission_notifications_desc
    DevicePermission.VPN -> R.string.permission_vpn_desc
}
