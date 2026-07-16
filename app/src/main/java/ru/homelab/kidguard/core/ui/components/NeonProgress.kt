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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Круговой индикатор прогресса с неоновым свечением (Glassmorphism стиль).
 *
 * @param progress прогресс от 0.0 до 1.0
 * @param modifier модификатор
 * @param size размер индикатора
 * @param strokeWidth толщина кольца
 * @param glowRadius радиус размытия внешнего свечения
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
    val sweepAngleDeg = 360f * animatedProgress

    // Градиент вдоль ВИДИМОЙ дуги: тусклее у старта (12 часов), ярче у текущей передней кромки —
    // так прогресс "светится" сильнее там, где он сейчас находится. brush = Brush.sweepGradient
    // тут не годится: его цветовые стопы размазаны по ВСЕЙ окружности (0–360°), а не по
    // нарисованному отрезку — на короткой дуге разница цветов почти не заметна.
    val gradientStart = accentColor.copy(alpha = 0.55f)
    val gradientEnd = lerp(accentColor, Color.White, 0.35f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Свечение — отдельный слой с НАСТОЯЩИМ блюром (RenderEffect, API 31+, у нас minSdk 33),
        // а не вторая плоская полупрозрачная дуга поверх/под основной: та давала жёсткую границу
        // вместо мягкого затухания и на глаз выглядела как "вторая дуга", а не свечение.
        Box(
            modifier = Modifier
                .size(size)
                .blur(radius = glowRadius * 3, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val strokeWidthPx = strokeWidth.toPx()
                val arcSize = Size(this.size.width - strokeWidthPx, this.size.height - strokeWidthPx)
                val topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
                val glowColor = accentColor.copy(alpha = 0.9f)
                drawGradientArc(
                    startAngleDeg = -90f,
                    sweepAngleDeg = sweepAngleDeg,
                    startColor = glowColor,
                    endColor = glowColor,
                    topLeft = topLeft,
                    arcSize = arcSize,
                    strokeWidthPx = strokeWidthPx + glowRadius.toPx() * 2
                )
            }
        }

        // Кольцо: фон + плавный градиент прогресса.
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidthPx = strokeWidth.toPx()
            val arcSize = Size(this.size.width - strokeWidthPx, this.size.height - strokeWidthPx)
            val topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            drawGradientArc(
                startAngleDeg = -90f,
                sweepAngleDeg = sweepAngleDeg,
                startColor = gradientStart,
                endColor = gradientEnd,
                topLeft = topLeft,
                arcSize = arcSize,
                strokeWidthPx = strokeWidthPx
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

/**
 * Дуга с реальным цветовым переходом от [startColor] к [endColor] вдоль [sweepAngleDeg] —
 * рисуется мелкими сегментами (Butt-кромка внутри, без "пупырышков" на стыках), а скруглённые
 * кромки на истинных концах дуги имитируются отдельными кружками нужного цвета.
 */
private fun DrawScope.drawGradientArc(
    startAngleDeg: Float,
    sweepAngleDeg: Float,
    startColor: Color,
    endColor: Color,
    topLeft: Offset,
    arcSize: Size,
    strokeWidthPx: Float
) {
    if (sweepAngleDeg <= 0f) return

    val segments = (sweepAngleDeg / 3f).toInt().coerceIn(2, 120)
    val segmentSweep = sweepAngleDeg / segments
    for (i in 0 until segments) {
        val t = i / (segments - 1f)
        drawArc(
            color = lerp(startColor, endColor, t),
            startAngle = startAngleDeg + i * segmentSweep,
            // Небольшой нахлёст компенсирует зазоры от округления между соседними сегментами.
            sweepAngle = segmentSweep + 0.5f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
        )
    }

    val center = topLeft + Offset(arcSize.width / 2f, arcSize.height / 2f)
    val radius = arcSize.width / 2f
    drawCircle(
        color = startColor,
        radius = strokeWidthPx / 2f,
        center = pointOnCircle(center, radius, startAngleDeg)
    )
    drawCircle(
        color = endColor,
        radius = strokeWidthPx / 2f,
        center = pointOnCircle(center, radius, startAngleDeg + sweepAngleDeg)
    )
}

private fun pointOnCircle(center: Offset, radius: Float, angleDeg: Float): Offset {
    val angleRad = angleDeg * (PI.toFloat() / 180f)
    return Offset(center.x + radius * cos(angleRad), center.y + radius * sin(angleRad))
}
