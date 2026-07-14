package ru.homelab.kidguard.platform.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import ru.homelab.kidguard.platform.R

/**
 * Device Admin приёмник KidGuard (НЕ Device Owner). Активный Device Admin защищает приложение от
 * удаления: удалить его нельзя, пока админ не деактивирован. Саму деактивацию перехватывает
 * [ru.homelab.kidguard.platform.accessibility.KidGuardAccessibilityService] PIN-оверлеем (веха 6.3).
 *
 * [onDisableRequested] — доп. барьер: системный экран отключения администратора показывает этот
 * текст-предупреждение.
 */
class KidGuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.device_admin_disable_warning)
}
