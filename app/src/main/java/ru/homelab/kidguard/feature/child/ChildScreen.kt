package ru.homelab.kidguard.feature.child

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.homelab.kidguard.core.ui.components.GlassBackground
import ru.homelab.kidguard.feature.child.rules.ChildAllowedAppsScreen
import ru.homelab.kidguard.feature.child.rules.ChildBlockedAppsScreen
import ru.homelab.kidguard.feature.child.rules.ChildLimitedAppsScreen
import ru.homelab.kidguard.feature.child.rules.ChildTodayStatsScreen
import ru.homelab.kidguard.feature.child.today.TodayScreen
import ru.homelab.kidguard.platform.foreground.KidGuardForegroundService

private const val ROUTE_CHILD_TODAY = "child/today"
private const val ROUTE_CHILD_LIMITS = "child/today/limits"
private const val ROUTE_CHILD_BLOCKED = "child/today/blocked"
private const val ROUTE_CHILD_ALLOWED = "child/today/allowed"
private const val ROUTE_CHILD_STATS = "child/today/stats"

/**
 * Точка входа детского режима. При входе запускает foreground-сервис контроля. Заводит вложенный
 * NavHost (Фаза UI-аудита): с главного экрана «Сегодня» карточки сетки 2×2 открывают детальные
 * read-only экраны списков правил — раньше был единственный экран без навигации.
 */
@Composable
fun ChildScreen(
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        KidGuardForegroundService.start(context)
    }

    val navController = rememberNavController()

    GlassBackground(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_CHILD_TODAY,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            composable(ROUTE_CHILD_TODAY) {
                TodayScreen(
                    onOpenPermissions = onOpenPermissions,
                    onOpenLimits = { navController.navigate(ROUTE_CHILD_LIMITS) },
                    onOpenBlocked = { navController.navigate(ROUTE_CHILD_BLOCKED) },
                    onOpenAllowed = { navController.navigate(ROUTE_CHILD_ALLOWED) },
                    onOpenStats = { navController.navigate(ROUTE_CHILD_STATS) }
                )
            }
            composable(ROUTE_CHILD_LIMITS) {
                ChildLimitedAppsScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_CHILD_BLOCKED) {
                ChildBlockedAppsScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_CHILD_ALLOWED) {
                ChildAllowedAppsScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_CHILD_STATS) {
                ChildTodayStatsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
