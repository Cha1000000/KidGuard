package ru.homelab.kidguard.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.homelab.kidguard.ui.theme.KidGuardTheme

/**
 * Стеклянный диалог (Glassmorphism) — замена androidx.compose.material3.AlertDialog.
 *
 * В отличие от [GlassCard], фон здесь почти непрозрачный: диалог ложится поверх произвольного
 * контента экрана и обязан оставаться читаемым, а не полагаться на градиентный фон приложения.
 *
 * @param onDismissRequest вызывается при закрытии диалога (клик вне окна, кнопка "назад")
 * @param confirmButton основная кнопка действия
 * @param modifier модификатор для внешнего контейнера (например, для ширины)
 * @param dismissButton опциональная кнопка отмены
 * @param title опциональный заголовок диалога
 * @param text опциональный текст-описание
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismissRequest) {
        val shape = RoundedCornerShape(28.dp)

        val backgroundColor = if (isSystemInDarkTheme()) {
            Color(0xFF17282E).copy(alpha = 0.96f)
        } else {
            Color(0xFFF6FAFB).copy(alpha = 0.98f)
        }

        val borderColors = if (isSystemInDarkTheme()) {
            listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.08f))
        } else {
            listOf(
                Color(0xFF2E6B7E).copy(alpha = 0.14f),
                Color(0xFF2E6B7E).copy(alpha = 0.07f)
            )
        }

        Box(
            modifier = modifier
                .clip(shape)
                .background(backgroundColor)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(colors = borderColors),
                    shape = shape
                )
                .padding(24.dp)
        ) {
            Column {
                if (title != null) {
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        title()
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (text != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium
                    ) {
                        text()
                    }
                    Spacer(Modifier.height(24.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun GlassDialogPreview() {
    KidGuardTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            GlassDialog(
                onDismissRequest = {},
                title = { Text("Сбросить лимит?") },
                text = { Text("Дневной лимит экранного времени для этого дня будет удалён.") },
                confirmButton = {
                    TextButton(onClick = {}) {
                        Text("Сбросить", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {}) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}
