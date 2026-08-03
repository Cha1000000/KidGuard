package ru.homelab.kidguard.feature.parent.about

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.R

/**
 * Экран «Пользовательское соглашение». Текст перенесён дословно из docs/legal/terms-of-use.md
 * (согласованный юридический документ) в строковые ресурсы terms_section*_title/p*. Списки
 * markdown превращены в текстовые перечисления — на телефоне так читается лучше.
 */
@Composable
fun TermsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sections = listOf(
        DocSection(
            titleRes = R.string.terms_section1_title,
            bodyRes = listOf(
                R.string.terms_section1_p1,
                R.string.terms_section1_p2,
                R.string.terms_section1_p3
            )
        ),
        DocSection(
            titleRes = R.string.terms_section2_title,
            bodyRes = listOf(
                R.string.terms_section2_p1,
                R.string.terms_section2_p2,
                R.string.terms_section2_p3,
                R.string.terms_section2_p4
            )
        ),
        DocSection(
            titleRes = R.string.terms_section3_title,
            bodyRes = listOf(
                R.string.terms_section3_p1,
                R.string.terms_section3_p2,
                R.string.terms_section3_p3
            )
        ),
        DocSection(
            titleRes = R.string.terms_section4_title,
            bodyRes = listOf(
                R.string.terms_section4_p1,
                R.string.terms_section4_p2,
                R.string.terms_section4_p3,
                R.string.terms_section4_p4,
                R.string.terms_section4_p5,
                R.string.terms_section4_p6
            )
        ),
        DocSection(
            titleRes = R.string.terms_section5_title,
            bodyRes = listOf(
                R.string.terms_section5_p1,
                R.string.terms_section5_p2,
                R.string.terms_section5_p3,
                R.string.terms_section5_p4,
                R.string.terms_section5_p5,
                R.string.terms_section5_p6,
                R.string.terms_section5_p7
            )
        ),
        DocSection(
            titleRes = R.string.terms_section6_title,
            bodyRes = listOf(R.string.terms_section6_p1, R.string.terms_section6_p2)
        ),
        DocSection(
            titleRes = R.string.terms_section7_title,
            bodyRes = listOf(
                R.string.terms_section7_p1,
                R.string.terms_section7_p2,
                R.string.terms_section7_p3,
                R.string.terms_section7_p4,
                R.string.terms_section7_p5
            )
        ),
        DocSection(
            titleRes = R.string.terms_section8_title,
            bodyRes = listOf(R.string.terms_section8_p1, R.string.terms_section8_p2)
        ),
        DocSection(
            titleRes = R.string.terms_section9_title,
            bodyRes = listOf(
                R.string.terms_section9_p1,
                R.string.terms_section9_p2,
                R.string.terms_section9_p3,
                R.string.terms_section9_p4
            )
        ),
        DocSection(
            titleRes = R.string.terms_section10_title,
            bodyRes = listOf(
                R.string.terms_section10_p1,
                R.string.terms_section10_p2,
                R.string.terms_section10_p3
            )
        ),
        DocSection(
            titleRes = R.string.terms_section11_title,
            bodyRes = listOf(R.string.terms_section11_p1)
        )
    )

    LegalDocumentScreen(
        title = stringResource(R.string.terms_title),
        sections = sections,
        onBack = onBack,
        modifier = modifier,
        header = {
            Text(
                text = stringResource(R.string.terms_effective_date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    )
}
