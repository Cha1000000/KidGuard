package ru.homelab.kidguard.feature.parent.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.homelab.kidguard.R

/**
 * Экран «Пользовательское соглашение». Текста ещё нет — вводный абзац и заглушка с пояснением,
 * что полный текст появится в следующем обновлении (составляется отдельным шагом).
 */
@Composable
fun TermsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sections = listOf(
        DocSection(bodyRes = listOf(R.string.terms_intro))
    )

    LegalDocumentScreen(
        title = stringResource(R.string.terms_title),
        sections = sections,
        onBack = onBack,
        modifier = modifier,
        footer = { DocTodoNote(text = stringResource(R.string.terms_todo)) }
    )
}
