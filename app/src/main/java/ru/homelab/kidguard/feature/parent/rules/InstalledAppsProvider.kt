package ru.homelab.kidguard.feature.parent.rules

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Запускаемое приложение: пакет, название и иконка (для экранов белого списка и лимитов). */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap
)

/**
 * Загружает список запускаемых приложений (у которых есть LAUNCHER-активность — то есть иконка
 * в лаунчере, как в Family Link). Тяжёлая операция — вызывать на фоновом диспетчере.
 * Единая точка для экранов «Всегда доступные» и «Лимиты приложений».
 */
class InstalledAppsProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun loadLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .distinctBy { it.activityInfo.packageName }
            .map {
                InstalledApp(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    icon = it.loadIcon(pm).toBitmap(width = ICON_PX, height = ICON_PX).asImageBitmap()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private companion object {
        const val ICON_PX = 96
    }
}
