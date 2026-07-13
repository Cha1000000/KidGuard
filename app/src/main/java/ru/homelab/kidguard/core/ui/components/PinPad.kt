package ru.homelab.kidguard.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.R

/**
 * Переиспользуемая клавиатура ввода PIN: точки-индикаторы введённых цифр + сетка 1-9, 0, ⌫.
 * Общий компонент для установки PIN у родителя (веха 6.1) и его офлайн-ввода у ребёнка (6.2) —
 * сам по себе состояния не хранит, только рисует переданное и сообщает о нажатиях наверх.
 *
 * @param enteredLength сколько цифр уже введено (для точек-индикаторов).
 * @param maxLength длина PIN (по умолчанию 4, как в макете).
 * @param isError показать индикаторы красным (неверный PIN) — сброс до обычного цвета на стороне
 *   вызывающего экрана (например, через небольшую задержку или следующий ввод).
 */
@Composable
fun PinPad(
    enteredLength: Int,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int = 4,
    isError: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        PinDots(enteredLength = enteredLength, maxLength = maxLength, isError = isError)
        PinKeypad(onDigit = onDigit, onBackspace = onBackspace)
    }
}

@Composable
private fun PinDots(enteredLength: Int, maxLength: Int, isError: Boolean) {
    val filledColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val emptyColor = if (isError) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(maxLength) { index ->
            val filled = index < enteredLength
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = if (filled) filledColor else emptyColor,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(onDigit: (Int) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9)
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit -> PinKey(onClick = { onDigit(digit) }) { Text(digit.toString()) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Пустая ячейка слева от «0», чтобы «0» и «⌫» встали под колонками «1»/«2» и «3».
            Box(modifier = Modifier.size(KEY_SIZE))
            PinKey(onClick = { onDigit(0) }) { Text("0") }
            // Глиф ⌫ вместо иконки: Backspace отсутствует в material-icons-core (только extended),
            // а тянуть extended ради одной иконки не хочется — глиф как в макете. Для читалок
            // с экрана — отдельное описание действия, а не сам символ.
            PinKey(onClick = onBackspace, contentDescription = stringResource(R.string.pin_backspace)) {
                Text(stringResource(R.string.pin_backspace_glyph))
            }
        }
    }
}

@Composable
private fun PinKey(
    onClick: () -> Unit,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(KEY_SIZE)
            .aspectRatio(1f)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            )
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                content()
            }
        }
    }
}

private val KEY_SIZE = 68.dp
