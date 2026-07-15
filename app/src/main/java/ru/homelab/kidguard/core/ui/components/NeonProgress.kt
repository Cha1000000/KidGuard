package ru.homelab.kidguard.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Круговой индикатор прогресса с неоновым свечением (Glassmorphism стиль).
 *
 * @param progress прогресс от 0.0 до 1.0
 * @param modifier модификатор
 * @param size размер индикатора
 * @param strokeWidth толщина кольца
 * @param glowRadius радиус свечения
 * @param accentColor основной акцентный цвет
 * @param trackColor цвет фона кольца
 * @param label верхняя подпись (например, "Осталось")
 * @param valueText основной текст (например, "01:20:00")
 * @param subtitleText нижняя подпись (например, "из 2ч 00м")
 */
@Composable
fun NeonProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    strokeWidth: Dp = 12.dp,
    glowRadius: Dp = 4.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = accentColor.copy(alpha = 0.08f),
    label: String? = null,
    valueText: String,
    subtitleText: String? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000),
        label = "progress"
    )

    // Градиент для кольца
    val gradientBrush = Brush.sweepGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.3f),
            accentColor,
            accentColor.copy(alpha = 0.3f)
        )
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Круговой индикатор
        Canvas(modifier = Modifier.size(size)) {
            val canvasSize = this.size
            val arcSize = Size(
                canvasSize.width - strokeWidth.toPx(),
                canvasSize.height - strokeWidth.toPx()
            )
            val topLeft = Offset(strokeWidth.toPx() / 2, strokeWidth.toPx() / 2)

            // Фон кольца
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // Активная часть с градиентом
            drawArc(
                brush = gradientBrush,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // Свечение (glow)
            drawArc(
                color = accentColor.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = (strokeWidth + glowRadius * 2).toPx(),
                    cap = StrokeCap.Round
                )
            )
        }

        // Текст в центре
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = valueText,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (subtitleText != null) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
