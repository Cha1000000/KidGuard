package ru.homelab.kidguard.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Крупный заголовок экрана-вкладки родительского режима (Правила / Дети / Статистика).
 *
 * @param actions необязательные действия справа от текста (напр. меню «три точки»). Правый
 * отступ строки уменьшен с 16dp до 4dp: стандартный IconButton — это 48dp зона нажатия с 24dp
 * иконкой внутри, т.е. уже даёт ~12dp визуального отступа сама по себе; 4dp+12dp возвращают
 * итоговые те же 16dp, что были у текста без actions.
 */
@Composable
fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}
