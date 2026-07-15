package ru.homelab.kidguard.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Стеклянная карточка (Glassmorphism) — полупрозрачный фон с тонкой светлой границей.
 *
 * @param modifier модификатор для внешнего контейнера
 * @param cornerRadius скругление углов (по умолчанию 24dp)
 * @param glassAlpha прозрачность фона (0.0–1.0, по умолчанию 0.15 для тёмной, 0.7 для светлой)
 * @param borderAlpha прозрачность границы (по умолчанию 0.2)
 * @param showShadow показывать ли тень (для светлой темы)
 * @param onClick обработчик клика (null = не кликабельна)
 * @param content содержимое карточки
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    glassAlpha: Float = if (isSystemInDarkTheme()) 0.15f else 0.7f,
    borderAlpha: Float = 0.2f,
    showShadow: Boolean = !isSystemInDarkTheme(),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    // Цвета для glassmorphism
    val glassColor = if (isSystemInDarkTheme()) {
        Color(0xFF17282E).copy(alpha = glassAlpha)
    } else {
        Color(0xFFDCEAEF).copy(alpha = 0.9f)
    }

    val borderColor = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = borderAlpha)
    } else {
        Color(0xFF2E6B7E).copy(alpha = 0.12f)
    }

    // Тень для светлой темы
    val shadowModifier = if (showShadow) {
        Modifier.shadow(
            elevation = 10.dp,
            shape = shape,
            ambientColor = Color(0xFF2E6B7E).copy(alpha = 0.08f),
            spotColor = Color(0xFF2E6B7E).copy(alpha = 0.08f)
        )
    } else {
        Modifier
    }

    // Кликабельность
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(shadowModifier)
            .clip(shape)
            .background(glassColor)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = borderAlpha * 0.5f)
                    )
                ),
                shape = shape
            )
            .then(clickableModifier)
            .padding(16.dp),
        content = content
    )
}


