package ru.homelab.kidguard.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.ui.theme.KidGuardTheme

/**
 * Линейный индикатор прогресса «шаг N из M» для мастеров (например, будущего мастера разрешений).
 *
 * @param currentStep сколько сегментов закрашено (1-индексация: currentStep=2 из totalSteps=5
 *   закрашивает первые 2 сегмента)
 * @param totalSteps общее число сегментов
 * @param modifier модификатор для внешнего контейнера
 * @param activeColor цвет закрашенного сегмента
 * @param inactiveColor цвет незакрашенного сегмента
 */
@Composable
fun LinearStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(totalSteps) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < currentStep) activeColor else inactiveColor)
            )
        }
    }
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun LinearStepIndicatorPreview() {
    KidGuardTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LinearStepIndicator(
                currentStep = 2,
                totalSteps = 5,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
