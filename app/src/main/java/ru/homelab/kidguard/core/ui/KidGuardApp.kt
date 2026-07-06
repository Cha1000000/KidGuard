package ru.homelab.kidguard.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.homelab.kidguard.core.domain.model.Role
import ru.homelab.kidguard.core.ui.navigation.Destinations
import ru.homelab.kidguard.feature.child.ChildScreen
import ru.homelab.kidguard.feature.onboarding.OnboardingScreen
import ru.homelab.kidguard.feature.onboarding.permissions.PermissionsWizardScreen
import ru.homelab.kidguard.feature.parent.ParentScreen

/**
 * Корень UI. По стартовому состоянию (роль выбрана или нет) поднимает навигацию с нужным
 * начальным экраном. Повторного логина/выхода нет — роль читается один раз из настроек.
 */
@Composable
fun KidGuardApp(
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel()
) {
    val state by viewModel.startState.collectAsStateWithLifecycle()

    when (val s = state) {
        AppStartState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is AppStartState.Ready -> {
            val navController = rememberNavController()
            // Фиксируем стартовый маршрут один раз. Иначе при выборе роли DataStore меняется,
            // startState пересчитывается, и смена startDestination пересоздала бы граф, перебив
            // императивную навигацию (напр. переход в мастер разрешений).
            val startRoute = remember { s.startRoute }
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = modifier
            ) {
                composable(Destinations.ONBOARDING) {
                    OnboardingScreen(
                        onRoleChosen = { role ->
                            // Родитель — сразу в свой режим. Ребёнок — сначала мастер разрешений.
                            val target = if (role == Role.PARENT) {
                                Destinations.PARENT
                            } else {
                                Destinations.PERMISSIONS
                            }
                            navController.navigate(target) {
                                popUpTo(Destinations.ONBOARDING) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Destinations.PERMISSIONS) {
                    PermissionsWizardScreen(
                        onFinished = {
                            navController.navigate(Destinations.CHILD) {
                                popUpTo(Destinations.PERMISSIONS) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Destinations.PARENT) { ParentScreen() }
                composable(Destinations.CHILD) { ChildScreen() }
            }
        }
    }
}
