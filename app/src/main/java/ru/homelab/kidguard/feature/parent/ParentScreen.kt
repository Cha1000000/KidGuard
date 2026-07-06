package ru.homelab.kidguard.feature.parent

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.homelab.kidguard.feature.parent.children.ChildrenScreen
import ru.homelab.kidguard.feature.parent.rules.RulesScreen
import ru.homelab.kidguard.feature.parent.statistics.StatisticsScreen

/**
 * Каркас родительского режима: нижняя навигация (Дети / Правила / Статистика) с вложенным
 * графом. Содержимое вкладок — заглушки, наполняются на следующих вехах.
 */
@Composable
fun ParentScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                ParentTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ParentTab.CHILDREN.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ParentTab.CHILDREN.route) { ChildrenScreen() }
            composable(ParentTab.RULES.route) { RulesScreen() }
            composable(ParentTab.STATISTICS.route) { StatisticsScreen() }
        }
    }
}
