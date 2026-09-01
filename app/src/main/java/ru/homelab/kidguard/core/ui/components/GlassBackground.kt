package ru.homelab.kidguard.core.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Градиентный фон для экранов в стиле Glassmorphism.
 *
 * Тёмная тема: глубокий тёмный градиент с бирюзовыми свечениями.
 * Светлая тема: светлый пастельный градиент с белыми бликами.
 *
 * @param modifier модификатор
 * @param content содержимое экрана
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()

    val colors = if (isDark) {
        listOf(
            Color(0xFF0E1C21),  // DarkBackground
            Color(0xFF04312B),  // DarkOnPrimary
            Color(0xFF0A161A)   // DarkSurfaceContainerLowest
        )
    } else {
        // Замерено по фирменному баннеру (KidGuard-banner.png): чистого белого в нём нет
        // вообще — самая светлая точка фона #E6F6F5, край уходит в #DDEFF3, и весь тон чуть
        // зеленее нашего прежнего. Раньше центр был #FFFFFF, из-за чего светлая тема читалась
        // как «просто белый фон» без фирменного оттенка.
        listOf(
            Color(0xFFEDF8F6),  // светлая бирюза, центр блика
            Color(0xFFE2F2F4),
            Color(0xFFD9ECF0)   // насыщенный край
        )
    }
    val centerFraction = if (isDark) Offset(0.3f, 0.2f) else Offset(0.7f, 0.3f)

    Box(
        modifier = modifier
            .fillMaxSize()
            // Brush.radialGradient принимает center/radius в пикселях канваса, а не в долях
            // экрана — считаем их из фактического размера через drawWithCache (даёт size),
            // иначе градиент сжимается в угол на реальных экранах.
            .drawWithCache {
                val brush = Brush.radialGradient(
                    colors = colors,
                    center = Offset(size.width * centerFraction.x, size.height * centerFraction.y),
                    radius = size.maxDimension * 0.85f
                )
                // Тёплый блик правее и выше центра — он есть на баннере (#FDF3E7, под золото
                // часов и замка на иконке) и даёт фону живость, которой не даёт одна бирюза.
                // Только в светлой теме: в тёмной такое пятно читалось бы грязным осветлением.
                val warmGlow = if (isDark) null else Brush.radialGradient(
                    colors = listOf(WarmGlow, WarmGlow.copy(alpha = 0f)),
                    center = Offset(size.width * 0.82f, size.height * 0.12f),
                    radius = size.maxDimension * 0.45f
                )
                onDrawBehind {
                    drawRect(brush)
                    warmGlow?.let { drawRect(it) }
                }
            },
        content = content
    )
}

/** Персиковый блик светлой темы — цвет взят с фирменного баннера (#FDF3E7). */
private val WarmGlow = Color(0x99FDF3E7)
