package ru.homelab.kidguard.platform.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.homelab.kidguard.core.domain.model.AppInfo
import ru.homelab.kidguard.core.domain.repository.InstalledAppsSource
import javax.inject.Inject

/**
 * Список запускаемых приложений устройства через PackageManager (веха 4.1).
 * Те же критерии, что у родительских экранов выбора: есть LAUNCHER-активность
 * (иконка в лаунчере, как в Family Link).
 */
class PlatformInstalledAppsSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) : InstalledAppsSource {

    override suspend fun launchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .distinctBy { it.activityInfo.packageName }
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }
}
