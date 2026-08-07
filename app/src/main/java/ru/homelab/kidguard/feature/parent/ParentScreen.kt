package ru.homelab.kidguard.feature.parent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.homelab.kidguard.core.ui.components.GlassBackground
import ru.homelab.kidguard.core.ui.components.GlassDockBar
import ru.homelab.kidguard.core.ui.components.GlassDockItem
import ru.homelab.kidguard.feature.parent.about.AboutScreen
import ru.homelab.kidguard.feature.parent.about.GuideScreen
import ru.homelab.kidguard.feature.parent.about.PrivacyPolicyScreen
import ru.homelab.kidguard.feature.parent.about.SupportScreen
import ru.homelab.kidguard.feature.parent.about.TermsScreen
import ru.homelab.kidguard.feature.parent.account.AccountScreen
import ru.homelab.kidguard.feature.parent.children.ChildrenScreen
import ru.homelab.kidguard.feature.parent.rules.AppLimitsScreen
import ru.homelab.kidguard.feature.parent.rules.BlockedAppsScreen
import ru.homelab.kidguard.feature.parent.rules.BlockedSitesScreen
import ru.homelab.kidguard.feature.parent.rules.BreaksScreen
import ru.homelab.kidguard.feature.parent.rules.DailyLimitScreen
import ru.homelab.kidguard.feature.parent.rules.PinSetupScreen
import ru.homelab.kidguard.feature.parent.rules.RulesScreen
import ru.homelab.kidguard.feature.parent.rules.ScheduleScreen
import ru.homelab.kidguard.feature.parent.rules.WhitelistScreen
import ru.homelab.kidguard.feature.parent.statistics.StatisticsScreen

private const val ROUTE_RULES_LIMIT = "parent/rules/limit"
private const val ROUTE_RULES_WHITELIST = "parent/rules/whitelist"
private const val ROUTE_RULES_APP_LIMITS = "parent/rules/app-limits"
private const val ROUTE_RULES_BLOCKED_APPS = "parent/rules/blocked-apps"
private const val ROUTE_RULES_BLOCKED_SITES = "parent/rules/blocked-sites"
private const val ROUTE_RULES_SCHEDULE = "parent/rules/schedule"
private const val ROUTE_RULES_PIN = "parent/rules/pin"
private const val ROUTE_RULES_BREAKS = "parent/rules/breaks"
private const val ROUTE_ACCOUNT = "parent/account"
private const val ROUTE_ABOUT = "parent/about"
private const val ROUTE_GUIDE = "parent/about/guide"
private const val ROUTE_PRIVACY = "parent/about/privacy"
private const val ROUTE_TERMS = "parent/about/terms"
private const val ROUTE_SUPPORT = "parent/about/support"

/**
 * Каркас родительского режима: нижняя навигация (Дети / Правила / Статистика) с вложенным
 * графом. Содержимое вкладок — заглушки, наполняются на следующих вехах.
 */
@Composable
fun ParentScreen(
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    // Поднимает петлю синхронизации политики (веха 4.3) на время жизни родительского режима.
    @Suppress("UNUSED_PARAMETER") syncViewModel: ParentSyncViewModel = hiltViewModel()
) {
    RequestNotificationPermission()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Скрываем DockBar на под-экранах Правил (лимит, белый список и т.д.) — точное сравнение,
    // а не startsWith: роут "parent/rules" — префикс всех под-роутов "parent/rules/...", поэтому
    // startsWith(it.route) был бы истинным и на под-экранах тоже.
    val showDockBar = ParentTab.entries.any { currentRoute == it.route }

    GlassBackground(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ParentTab.CHILDREN.route,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            composable(ParentTab.CHILDREN.route) {
                ChildrenScreen(
                    onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                    onOpenAccount = { navController.navigate(ROUTE_ACCOUNT) }
                )
            }
            composable(ParentTab.RULES.route) {
                RulesScreen(
                    onOpenDailyLimit = { navController.navigate(ROUTE_RULES_LIMIT) },
                    onOpenAppLimits = { navController.navigate(ROUTE_RULES_APP_LIMITS) },
                    onOpenBlockedApps = { navController.navigate(ROUTE_RULES_BLOCKED_APPS) },
                    onOpenBlockedSites = { navController.navigate(ROUTE_RULES_BLOCKED_SITES) },
                    onOpenSchedule = { navController.navigate(ROUTE_RULES_SCHEDULE) },
                    onOpenWhitelist = { navController.navigate(ROUTE_RULES_WHITELIST) },
                    onOpenPinProtection = { navController.navigate(ROUTE_RULES_PIN) },
                    onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                    onOpenAccount = { navController.navigate(ROUTE_ACCOUNT) }
                )
            }
            composable(ROUTE_RULES_LIMIT) {
                DailyLimitScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBreaks = { navController.navigate(ROUTE_RULES_BREAKS) }
                )
            }
            composable(ROUTE_RULES_BREAKS) {
                BreaksScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_RULES_APP_LIMITS) {
                AppLimitsScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_RULES_BLOCKED_APPS) {
                BlockedAppsScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_RULES_BLOCKED_SITES) {
                BlockedSitesScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_RULES_SCHEDULE) {
                ScheduleScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPinSetup = { navController.navigate(ROUTE_RULES_PIN) }
                )
            }
            composable(ROUTE_RULES_WHITELIST) {
                WhitelistScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_RULES_PIN) {
                PinSetupScreen(onBack = { navController.popBackStack() })
            }
            composable(ParentTab.STATISTICS.route) {
                StatisticsScreen(
                    onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                    onOpenAccount = { navController.navigate(ROUTE_ACCOUNT) }
                )
            }
            composable(ROUTE_ACCOUNT) {
                AccountScreen(
                    onBack = { navController.popBackStack() },
                    onSignedOut = onSignedOut
                )
            }
            composable(ROUTE_ABOUT) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGuide = { navController.navigate(ROUTE_GUIDE) },
                    onOpenPrivacy = { navController.navigate(ROUTE_PRIVACY) },
                    onOpenTerms = { navController.navigate(ROUTE_TERMS) },
                    onOpenSupport = { navController.navigate(ROUTE_SUPPORT) }
                )
            }
            composable(ROUTE_GUIDE) {
                GuideScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_PRIVACY) {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_TERMS) {
                TermsScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_SUPPORT) {
                SupportScreen(onBack = { navController.popBackStack() })
            }
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

/**
 * Разрешение на уведомления у родителя (Android 13+). Без него сообщения «контроль на телефоне
 * ребёнка сломан» не показываются вовсе — а это единственный способ узнать о поломке, не открывая
 * приложение. Спрашиваем один раз при входе в родительский режим: системный диалог сам больше не
 * появится, если родитель уже ответил.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
