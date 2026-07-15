package ru.homelab.kidguard.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val backgroundBrush = if (isDark) {
        // Тёмная тема: градиент от тёмно-бирюзовому к чёрному
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF0E1C21),  // DarkBackground
                Color(0xFF04312B),  // DarkOnPrimary
                Color(0xFF0A161A)   // DarkSurfaceContainerLowest
            ),
            center = Offset(0.3f, 0.2f),
            radius = 1200f
        )
    } else {
        // Светлая тема: градиент от белого к светло-серому
        Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFFFF),  // White
                Color(0xFFEEF4F6),  // LightBackground
                Color(0xFFDCEAEF)   // LightSurfaceContainer
            ),
            center = Offset(0.7f, 0.3f),
            radius = 1000f
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        content = content
    )
}

/**
 * Расширение для Color — вычисляет яркость (luminance).
 */
private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
