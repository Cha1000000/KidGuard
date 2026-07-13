package ru.homelab.kidguard.feature.child

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ru.homelab.kidguard.feature.child.today.TodayScreen
import ru.homelab.kidguard.platform.foreground.KidGuardForegroundService

/**
 * Точка входа детского режима. При входе запускает foreground-сервис контроля и показывает
 * главный экран «Сегодня» (веха 4.1.3).
 */
@Composable
fun ChildScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        KidGuardForegroundService.start(context)
    }
    TodayScreen(modifier = modifier)
}
