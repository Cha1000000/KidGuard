package ru.homelab.kidguard.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Полный проход блика по элементу. Медленнее секунды выглядит вязко, быстрее — суетливо. */
private const val SHIMMER_DURATION_MS = 1200

/**
 * Бегущий блик поверх заглушки-скелетона.
 *
 * Своя реализация вместо библиотеки шиммера: это всего лишь линейный градиент, который ездит по
 * элементу, а отдельная зависимость привязала бы нас к чужому релизному циклу вслед за версиями
 * Compose — при том что цвета всё равно берутся из нашей «стеклянной» палитры.
 *
 * Блик рисуется ПОВЕРХ содержимого, поэтому применять модификатор нужно к элементу, у которого
 * уже есть фон (см. [ShimmerBox]).
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_DURATION_MS),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-progress"
    )
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val transparent = highlight.copy(alpha = 0f)
    return this.drawWithCache {
        // Блик шире самого элемента и стартует за его левой границей, чтобы вход и выход были
        // плавными, а не появлялись рывком у края.
        val width = size.width
        val start = -width + width * 2f * progress
        val brush = Brush.linearGradient(
            colors = listOf(transparent, highlight, transparent),
            start = Offset(start, 0f),
            end = Offset(start + width, size.height)
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush)
        }
    }
}

/**
 * Прямоугольник-заглушка на месте ещё не загруженного содержимого.
 *
 * Габариты задаёт вызывающий и обязан ставить их равными габаритам готового элемента — иначе
 * после загрузки экран дёрнется, а ради этого скелетон и нужен.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .shimmer()
    )
}
