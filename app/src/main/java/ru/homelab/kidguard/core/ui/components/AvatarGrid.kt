package ru.homelab.kidguard.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

private const val AVATAR_GRID_COLUMNS = 4

/**
 * Сетка выбора аватара в [AVATAR_GRID_COLUMNS] колонки. Ячейки квадратные и делят доступную
 * ширину поровну через `weight` — размер и отступы адаптируются под любой экран, ничего не
 * обрезается и не наезжает. Неполный последний ряд добивается невидимыми ячейками, чтобы
 * элементы не растягивались.
 */
@Composable
fun AvatarGrid(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val spacing = 12.dp
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        ChildAvatars.all.indices.chunked(AVATAR_GRID_COLUMNS).forEach { rowIndices ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing), modifier = Modifier.fillMaxWidth()) {
                rowIndices.forEach { index ->
                    val isSelected = index == selected
                    Image(
                        painter = painterResource(ChildAvatars.resFor(index)),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier
                            )
                            .clickable { onSelect(index) }
                    )
                }
                // Добить неполный ряд пустыми ячейками, чтобы аватарки не растянулись на всю ширину.
                repeat(AVATAR_GRID_COLUMNS - rowIndices.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
