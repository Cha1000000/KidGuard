package ru.homelab.kidguard.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.ui.theme.DangerAccentDark
import ru.homelab.kidguard.ui.theme.DangerAccentLight
import ru.homelab.kidguard.ui.theme.KidGuardTheme

/**
 * Переиспользуемая «опасная» кнопка в стиле глассморфизма (например, "Заблокировать на сегодня").
 * Инкапсулирует повторяющийся паттерн ручной сборки OutlinedButton с DangerAccent-цветами.
 *
 * @param onClick обработчик клика
 * @param modifier модификатор кнопки
 * @param enabled доступна ли кнопка
 * @param shape форма кнопки
 * @param contentPadding внутренние отступы содержимого
 * @param content содержимое кнопки
 */
@Composable
fun GlassDangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val dangerColor = if (isSystemInDarkTheme()) DangerAccentDark else DangerAccentLight
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor),
        border = BorderStroke(1.dp, dangerColor.copy(alpha = if (enabled) 1f else 0.3f)),
        modifier = modifier,
        content = content
    )
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun GlassDangerButtonPreview() {
    KidGuardTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            GlassDangerButton(onClick = {}, modifier = Modifier.padding(16.dp)) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = "Заблокировать", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
