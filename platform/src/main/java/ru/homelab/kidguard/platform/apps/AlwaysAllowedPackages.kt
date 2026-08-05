package ru.homelab.kidguard.platform.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Технически всегда разрешённые пакеты: само KidGuard и домашний лаунчер. Это НЕ родительский
 * список «Всегда доступные» (он лежит в политике как `whitelist`) — эти два пакета ребёнок
 * не выбирает и родитель не настраивает.
 *
 * Вынесено из `BlockingController` в отдельный компонент, потому что то же множество нужно
 * движку учёта: время на домашнем экране и в самом KidGuard не должно расходовать дневной лимит,
 * раз лимит эти пакеты и не закрывает.
 *
 * Набор вычисляется один раз: `resolveActivity` не бесплатен, а трекер спрашивает его каждый тик.
 * Смена лаунчера по умолчанию подхватится при следующем запуске сервиса — ровно как было раньше
 * внутри `BlockingController`.
 */
@Singleton
class AlwaysAllowedPackages @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    val packages: Set<String> by lazy {
        buildSet {
            add(context.packageName)
            addAll(resolveLauncherPackages())
        }
    }

    private fun resolveLauncherPackages(): Set<String> {
        // Только текущий домашний лаунчер по умолчанию. queryIntentActivities(HOME) захватывает
        // и служебные HOME-активности (напр. Settings.FallbackHome), поэтому берём default.
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val packageName = context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
        return setOfNotNull(packageName)
    }
}
