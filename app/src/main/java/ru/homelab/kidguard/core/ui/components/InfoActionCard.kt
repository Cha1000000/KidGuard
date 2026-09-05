package ru.homelab.kidguard.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.ui.theme.KidGuardTheme

/**
 * Стеклянная карточка-инструкция: заголовок + описание + кнопка действия.
 * Обобщённая замена дублирующихся карточек вроде AutostartCard/AlwaysOnVpnCard.
 *
 * @param title заголовок карточки
 * @param description поясняющий текст
 * @param actionLabel текст кнопки действия; null — карточка без кнопки
 * @param onAction обработчик клика; null — карточка без кнопки
 * @param modifier модификатор для внешнего контейнера
 *
 * Кнопка необязательна: часть шагов настройки нельзя открыть интентом вообще (например, замок
 * карточки в списке последних приложений — системного экрана для него не существует), и такой шаг
 * остаётся чистой инструкцией. Рисовать кнопку, которая никуда не ведёт, хуже, чем не рисовать.
 */
@Composable
fun InfoActionCard(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 12.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun InfoActionCardPreview() {
    KidGuardTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            InfoActionCard(
                title = "Автозапуск в фоне",
                description = "На оболочках вроде HiOS, MIUI, EMUI есть свой список " +
                    "автозапуска — помимо обычной оптимизации батареи. Разрешите KidGuard " +
                    "автозапуск и работу в фоне.",
                actionLabel = "Открыть настройки автозапуска",
                onAction = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
