package ru.homelab.kidguard.feature.parent.about

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassCard

/**
 * Блок документа: заголовок раздела (может отсутствовать — тогда рисуются только абзацы) и его
 * абзацы. Раздел может быть и «пустым носителем заголовка» (bodyRes пуст) — так собираются
 * вложенные подзаголовки вида «2.1. …» внутри более крупного раздела «2. …».
 */
data class DocSection(@StringRes val titleRes: Int? = null, val bodyRes: List<Int> = emptyList())

/**
 * Переиспользуемая вёрстка документа-справки (руководство, политика, соглашение): шапка,
 * прокручиваемый список разделов с абзацами, необязательные шапка/подвал сверху и снизу списка.
 */
@Composable
fun LegalDocumentScreen(
    title: String,
    sections: List<DocSection>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxSize()) {
        CompactTopBar(title = title, onBack = onBack)

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            if (header != null) {
                item { header() }
            }
            sections.forEach { section ->
                if (section.titleRes != null) {
                    item {
                        Text(
                            text = stringResource(section.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                        )
                    }
                }
                items(section.bodyRes) { bodyRes ->
                    // Межабзацный интервал — обычный line-height bodyMedium без ужимания, текст
                    // юридического документа должен нормально читаться на телефоне.
                    Text(
                        text = stringResource(bodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }
            if (footer != null) {
                item { footer() }
            }
        }
    }
}

/** Приглушённая заметка-заглушка внизу документа («текст появится позже»). */
@Composable
fun DocTodoNote(text: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
