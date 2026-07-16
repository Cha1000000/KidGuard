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
        listOf(
            Color(0xFFFFFFFF),  // White
            Color(0xFFEEF4F6),  // LightBackground
            Color(0xFFDCEAEF)   // LightSurfaceContainer
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
                onDrawBehind { drawRect(brush) }
            },
        content = content
    )
}
