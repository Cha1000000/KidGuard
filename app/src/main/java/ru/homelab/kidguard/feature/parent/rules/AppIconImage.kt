package ru.homelab.kidguard.feature.parent.rules

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * Иконка приложения ребёнка. Если локальной иконки нет (приложение не установлено на телефоне
 * родителя) — буквенный кружок с первой буквой названия на детерминированном по пакету цвете.
 */
@Composable
fun AppIconImage(icon: ImageBitmap?, label: String, packageName: String, modifier: Modifier = Modifier) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
        )
    } else {
        val background = FallbackColors[packageName.hashCode().absoluteValue % FallbackColors.size]
        Box(
            modifier = modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/** Приглушённая палитра для буквенных кружков (белая буква читается на всех цветах). */
private val FallbackColors = listOf(
    Color(0xFF7E57C2), // фиолетовый
    Color(0xFF42A5F5), // голубой
    Color(0xFF26A69A), // бирюзовый
    Color(0xFFEF7043), // терракотовый
    Color(0xFFEC627B), // розовый
    Color(0xFF9CCC65), // салатовый
    Color(0xFF5C7CFA), // синий
    Color(0xFFFFB300)  // янтарный
)
