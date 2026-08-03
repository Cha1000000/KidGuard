package ru.homelab.kidguard.feature.parent.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.homelab.kidguard.R

/**
 * Экран «Как пользоваться». Пока — короткая структура «с чего начать» (по согласованному
 * макету, экран 4); полный текст руководства со скриншотами по каждой функции — отдельная задача.
 */
@Composable
fun GuideScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sections = listOf(
        DocSection(
            titleRes = R.string.guide_start_title,
            bodyRes = listOf(
                R.string.guide_step1,
                R.string.guide_step2,
                R.string.guide_step3
            )
        )
    )

    LegalDocumentScreen(
        title = stringResource(R.string.guide_title),
        sections = sections,
        onBack = onBack,
        modifier = modifier,
        footer = { DocTodoNote(text = stringResource(R.string.guide_todo)) }
    )
}
