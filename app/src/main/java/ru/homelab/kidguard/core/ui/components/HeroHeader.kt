package ru.homelab.kidguard.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.ui.theme.KidGuardTheme

/**
 * Общий заголовок-«визитка» экрана: иконка-бейдж + заголовок + подзаголовок.
 * Обобщённая замена дублирующихся заголовков в SignInScreen/PairingScreen.
 *
 * @param iconPainter иконка в бейдже
 * @param title заголовок экрана
 * @param subtitle подзаголовок/пояснение
 * @param modifier модификатор для внешнего контейнера
 * @param badgeSize размер бейджа
 * @param badgeCornerRadius скругление углов бейджа
 * @param iconSize размер иконки внутри бейджа
 * @param badgeColor цвет фона бейджа
 * @param iconTint цвет иконки
 */
@Composable
fun HeroHeader(
    iconPainter: Painter,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 96.dp,
    badgeCornerRadius: Dp = 26.dp,
    iconSize: Dp = 56.dp,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    iconTint: Color = MaterialTheme.colorScheme.onPrimary
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(badgeCornerRadius),
            color = badgeColor,
            modifier = Modifier.size(badgeSize)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp)
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun HeroHeaderPreview() {
    KidGuardTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HeroHeader(
                iconPainter = rememberVectorPainter(Icons.Filled.Lock),
                title = "Вход",
                subtitle = "Войдите через Google",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            )
        }
    }
}
