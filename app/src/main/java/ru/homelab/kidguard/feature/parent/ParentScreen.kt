package ru.homelab.kidguard.feature.parent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.homelab.kidguard.core.ui.components.GlassDockBar
import ru.homelab.kidguard.core.ui.components.GlassDockItem
import ru.homelab.kidguard.feature.parent.children.ChildrenScreen
import ru.homelab.kidguard.feature.parent.rules.AppLimitsScreen
import ru.homelab.kidguard.feature.parent.rules.BlockedAppsScreen
import ru.homelab.kidguard.feature.parent.rules.DailyLimitScreen
import ru.homelab.kidguard.feature.parent.rules.PinSetupScreen
import ru.homelab.kidguard.feature.parent.rules.RulesScreen
import ru.homelab.kidguard.feature.parent.rules.WhitelistScreen
import ru.homelab.kidguard.feature.parent.statistics.StatisticsScreen

private const val ROUTE_RULES_LIMIT = "parent/rules/limit"
private const val ROUTE_RULES_WHITELIST = "parent/rules/whitelist"
private const val ROUTE_RULES_APP_LIMITS = "parent/rules/app-limits"
private const val ROUTE_RULES_BLOCKED_APPS = "parent/rules/blocked-apps"
private const val ROUTE_RULES_PIN = "parent/rules/pin"

/**
 * Каркас родительского режима: нижняя навигация (Дети / Правила / Статистика) с вложенным
 * графом. Содержимое вкладок — заглушки, наполняются на следующих вехах.
 */
@Composable
fun ParentScreen(
    modifier: Modifier = Modifier,
    // Поднимает петлю синхронизации политики (веха 4.3) на время жизни родительского режима.
    @Suppress("UNUSED_PARAMETER") syncViewModel: ParentSyncViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Скрываем DockBar на под-экранах Правил (лимит, белый список и т.д.)
    val showDockBar = ParentTab.entries.any { currentRoute?.startsWith(it.route) == true }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ParentTab.CHILDREN.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(ParentTab.CHILDREN.route) { ChildrenScreen() }
            composable(ParentTab.RULES.route) {
                RulesScreen(
                    onOpenDailyLimit = { navController.navigate(ROUTE_RULES_LIMIT) },
                    onOpenAppLimits = { navController.navigate(ROUTE_RULES_APP_LIMITS) },
                    onOpenBlockedApps = { navController.navigate(ROUTE_RULES_BLOCKED_APPS) },
                    onOpenWhitelist = { navController.navigate(ROUTE_RULES_WHITELIST) },
                    onOpenPinProtection = { navController.navigate(ROUTE_RULES_PIN) }
                )
            }
            composable(ROUTE_RULES_LIMIT) {
                DailyLimitScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_RULES_APP_LIMITS) {
                AppLimitsScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_RULES_BLOCKED_APPS) {
                BlockedAppsScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_RULES_WHITELIST) {
                WhitelistScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_RULES_PIN) {
                PinSetupScreen(onBack = { navController.popBackStack() })
            }
            composable(ParentTab.STATISTICS.route) { StatisticsScreen() }
        }

        // Плавающий Glass Dock Bar поверх контента
        if (showDockBar) {
            GlassDockBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                ParentTab.entries.forEach { tab ->
                    GlassDockItem(
                        icon = tab.icon,
                        label = stringResource(tab.labelRes),
                        selected = currentRoute?.startsWith(tab.route) == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
}
