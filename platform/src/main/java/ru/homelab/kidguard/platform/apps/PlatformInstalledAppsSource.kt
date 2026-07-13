package ru.homelab.kidguard.platform.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import ru.homelab.kidguard.core.domain.model.AppInfo
import ru.homelab.kidguard.core.domain.repository.InstalledAppsSource
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Список запускаемых приложений устройства через PackageManager (веха 4.1).
 * Те же критерии, что у родительских экранов выбора: есть LAUNCHER-активность
 * (иконка в лаунчере, как в Family Link). Иконка сжимается в WebP 96×96 и кодируется
 * в base64 — публикуется на сервер, чтобы родитель видел её на своих экранах.
 */
class PlatformInstalledAppsSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) : InstalledAppsSource {

    override suspend fun launchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .distinctBy { it.activityInfo.packageName }
            .map {
                AppInfo(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                    iconBase64 = runCatching { it.loadIcon(pm).toIconBase64() }.getOrNull()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    override suspend fun installedPackageNames(): List<String> = withContext(Dispatchers.IO) {
        currentInstalledPackageNames()
    }

    override fun observeInstalledPackageNames(): Flow<List<String>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(currentInstalledPackageNames())
            }
        }
        // PACKAGE_ADDED/REMOVED/REPLACED — protected system broadcasts, поэтому receiver
        // регистрируем как NOT_EXPORTED. Схема "package" обязательна для этих действий.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(currentInstalledPackageNames()) // начальный снимок
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun currentInstalledPackageNames(): List<String> =
        context.packageManager.getInstalledApplications(0).map { it.packageName }

    private fun android.graphics.drawable.Drawable.toIconBase64(): String {
        val stream = ByteArrayOutputStream()
        toBitmap(width = ICON_PX, height = ICON_PX)
            .compress(Bitmap.CompressFormat.WEBP_LOSSY, ICON_QUALITY, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private companion object {
        const val ICON_PX = 96
        const val ICON_QUALITY = 80
    }
}
