package ru.homelab.kidguard.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Стеклянный переключатель (Glass Toggle) с эффектом свечения в активном состоянии.
 *
 * @param checked состояние переключателя
 * @param onCheckedChange обработчик изменения состояния
 * @param modifier модификатор
 * @param width ширина переключателя
 * @param height высота переключателя
 * @param thumbSize размер бегунка
 * @param accentColor акцентный цвет (для активного состояния)
 * @param trackColorInactive цвет дорожки в неактивном состоянии
 */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 52.dp,
    height: Dp = 28.dp,
    thumbSize: Dp = 22.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    // Дефолт зависит от темы: белая дорожка с alpha 0.1 (задумывалась для тёмного GlassCard)
    // почти не видна на светлой GlassCard (фон rgba(220,234,239,0.9)) — выключенный тоггл выглядит
    // как отсутствующий переключатель. В светлой теме используем приглушённый тёмный оттенок.
    trackColorInactive: Color = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color(0xFF2E6B7E).copy(alpha = 0.22f)
    }
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Анимация цвета дорожки
    val trackColor by animateColorAsState(
        targetValue = if (checked) accentColor else trackColorInactive,
        animationSpec = tween(durationMillis = 300),
        label = "trackColor"
    )

    // Анимация позиции бегунка
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) width - thumbSize - 6.dp else 2.dp,
        animationSpec = tween(durationMillis = 300),
        label = "thumbOffset"
    )

    // Анимация свечения
    val glowAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (checked) 0.4f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "glowAlpha"
    )

    val trackShape = RoundedCornerShape(height / 2)
    val thumbShape = CircleShape

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(trackShape)
            .background(trackColor)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = trackShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(3.dp)
    ) {
        // Бегунок с эффектом свечения
        Box(
            modifier = Modifier
                .size(thumbSize)
                .offset(x = thumbOffset)
                .shadow(
                    elevation = if (checked) 8.dp else 2.dp,
                    shape = thumbShape,
                    ambientColor = accentColor.copy(alpha = glowAlpha),
                    spotColor = accentColor.copy(alpha = glowAlpha)
                )
                .background(Color.White, thumbShape)
        )
    }
}
