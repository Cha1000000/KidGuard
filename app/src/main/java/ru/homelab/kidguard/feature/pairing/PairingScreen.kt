package ru.homelab.kidguard.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.GlassBackground

/**
 * Экран привязки детского устройства (веха 4.2): ребёнок вводит 6-значный код от родителя.
 * Показывается после выбора роли «ребёнок» вместо Google-входа (см. навигацию). При успехе
 * [onPaired] уводит дальше в детский флоу (мастер разрешений).
 */
@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val code by viewModel.code.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState) {
        if (uiState is PairingUiState.Success) onPaired()
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    GlassBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_link),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.pairing_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 22.dp)
            )
            Text(
                text = stringResource(R.string.pairing_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 30.dp)
            )

            CodeInput(
                code = code,
                enabled = uiState !is PairingUiState.Loading,
                onCodeChange = viewModel::onCodeChange,
                onImeDone = viewModel::submit,
                focusRequester = focusRequester
            )

            if (uiState is PairingUiState.Error) {
                Text(
                    text = stringResource(R.string.pairing_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            if (uiState is PairingUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                Text(
                    text = stringResource(R.string.pairing_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                Button(
                    onClick = viewModel::submit,
                    enabled = code.length == PAIRING_CODE_LENGTH,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    Text(stringResource(R.string.pairing_button))
                }
                Text(
                    text = stringResource(R.string.pairing_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
    }
}

/**
 * Шесть ячеек ввода поверх одного невидимого [BasicTextField]: поле держит фокус и клавиатуру,
 * а ячейки лишь отрисовывают введённые цифры (подсвечивая текущую позицию).
 */
@Composable
private fun CodeInput(
    code: String,
    enabled: Boolean,
    onCodeChange: (String) -> Unit,
    onImeDone: () -> Unit,
    focusRequester: FocusRequester
) {
    BasicTextField(
        value = code,
        onValueChange = onCodeChange,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onImeDone() }),
        modifier = Modifier.focusRequester(focusRequester),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(PAIRING_CODE_LENGTH) { index ->
                    CodeCell(
                        char = code.getOrNull(index)?.toString().orEmpty(),
                        focused = index == code.length
                    )
                }
            }
        }
    )
}

@Composable
private fun CodeCell(char: String, focused: Boolean) {
    // Полупрозрачная стеклянная ячейка (та же формула цвета, что у GlassCard) — сплошной
    // colorScheme.surface на градиентном GlassBackground выглядел бы плашкой из другого дизайна.
    val isDark = isSystemInDarkTheme()
    val glassColor = if (isDark) {
        Color(0xFF17282E).copy(alpha = 0.4f)
    } else {
        Color(0xFFDCEAEF).copy(alpha = 0.9f)
    }
    val borderColor = when {
        focused || char.isNotEmpty() -> MaterialTheme.colorScheme.primary
        isDark -> Color.White.copy(alpha = 0.2f)
        else -> Color(0xFF2E6B7E).copy(alpha = 0.2f)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 46.dp, height = 58.dp)
            .background(glassColor, RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Text(
            text = char,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
