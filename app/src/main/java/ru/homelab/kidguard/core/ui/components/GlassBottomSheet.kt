package ru.homelab.kidguard.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.ui.theme.KidGuardTheme

/**
 * Стеклянное модальное нижнее меню (Glassmorphism) — тонкая обёртка над
 * androidx.compose.material3.ModalBottomSheet с фирменным непрозрачным фоном.
 *
 * @param onDismissRequest вызывается при закрытии шторки
 * @param modifier модификатор для внешнего контейнера
 * @param sheetState состояние шторки
 * @param content содержимое шторки
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor = if (isSystemInDarkTheme()) {
        Color(0xFF17282E).copy(alpha = 0.98f)
    } else {
        Color(0xFFF6FAFB).copy(alpha = 0.99f)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = containerColor,
        content = content
    )
}

/**
 * ModalBottomSheet — оверлей с анимацией снизу, статичный @Preview его не рендерит корректно.
 * Поэтому для превью визуализируем эквивалентное содержимое (тот же containerColor и форма)
 * как обычную Column вместо реального [GlassBottomSheet].
 */
@Composable
private fun GlassBottomSheetPreviewContent() {
    val containerColor = if (isSystemInDarkTheme()) {
        Color(0xFF17282E).copy(alpha = 0.98f)
    } else {
        Color(0xFFF6FAFB).copy(alpha = 0.99f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(containerColor)
            .padding(24.dp)
    ) {
        Text(
            text = "Выбрать аватар",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Аватар отображается в списке детей и на экране ребёнка.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("Готово")
        }
    }
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun GlassBottomSheetPreview() {
    KidGuardTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            GlassBottomSheetPreviewContent()
        }
    }
}
