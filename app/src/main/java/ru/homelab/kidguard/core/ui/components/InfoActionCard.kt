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
 * @param actionLabel текст кнопки действия
 * @param onAction обработчик клика по кнопке действия
 * @param modifier модификатор для внешнего контейнера
 */
@Composable
fun InfoActionCard(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
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
            OutlinedButton(onClick = onAction, modifier = Modifier.padding(top = 12.dp)) {
                Text(actionLabel)
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
