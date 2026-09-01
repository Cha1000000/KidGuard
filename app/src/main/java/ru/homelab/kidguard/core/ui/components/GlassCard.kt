package ru.homelab.kidguard.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
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
 * @param contentPadding внутренние отступы содержимого (по умолчанию 16dp со всех сторон)
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
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    // Цвета для glassmorphism
    // Светлая тема: заливка белая, а не голубая. Прежний #DCEAEF под 50% давал ровно цвет
    // фона (замер: карточка #E6EFF0 против фона #E6F3F2 — контраст 1.04, карточки не видно).
    // Белым карточка хотя бы приподнимается над фоном; вытянуть её одной заливкой всё равно
    // нельзя — оба цвета у верхней границы яркости, поэтому форму держат граница и тень ниже.
    val glassColor = if (isSystemInDarkTheme()) {
        Color(0xFF17282E).copy(alpha = glassAlpha)
    } else {
        Color.White.copy(alpha = 0.65f)
    }

    // В светлой теме белая граница невидима на светлом фоне — берём приглушённый primary и
    // делаем его вдвое заметнее прежних 12%: в светлом glassmorphism именно граница, а не
    // заливка, очерчивает карточку.
    val borderColor = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = borderAlpha)
    } else {
        Color(0xFF2E6B7E).copy(alpha = 0.24f)
    }

    // Тень для светлой темы
    val shadowModifier = if (showShadow) {
        Modifier.shadow(
            elevation = 10.dp,
            shape = shape,
            // Тень — вторая половина того, чем карточка отделяется от фона; прежние 8%
            // читались только на белом, а на нашем цветном фоне пропадали совсем.
            ambientColor = Color(0xFF2E6B7E).copy(alpha = 0.16f),
            spotColor = Color(0xFF2E6B7E).copy(alpha = 0.16f)
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
            .padding(contentPadding),
        content = content
    )
}


