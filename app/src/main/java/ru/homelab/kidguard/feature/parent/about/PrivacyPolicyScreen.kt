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
 * Экран политики конфиденциальности. Текст перенесён дословно из docs/legal/privacy-policy.md
 * (согласованный юридический документ) в строковые ресурсы privacy_section*_title/p*. Таблицы
 * markdown превращены в текстовые перечисления — на телефоне так читается лучше.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sections = listOf(
        DocSection(
            titleRes = R.string.privacy_section1_title,
            bodyRes = listOf(
                R.string.privacy_section1_p1,
                R.string.privacy_section1_p2,
                R.string.privacy_section1_p3,
                R.string.privacy_section1_p4
            )
        ),
        DocSection(titleRes = R.string.privacy_section2_title),
        DocSection(
            titleRes = R.string.privacy_section2_1_title,
            bodyRes = listOf(R.string.privacy_section2_1_p1, R.string.privacy_section2_1_p2)
        ),
        DocSection(
            titleRes = R.string.privacy_section2_2_title,
            bodyRes = listOf(R.string.privacy_section2_2_p1, R.string.privacy_section2_2_p2)
        ),
        DocSection(
            titleRes = R.string.privacy_section2_3_title,
            bodyRes = listOf(
                R.string.privacy_section2_3_p1,
                R.string.privacy_section2_3_p2,
                R.string.privacy_section2_3_p3
            )
        ),
        DocSection(
            titleRes = R.string.privacy_section2_4_title,
            bodyRes = listOf(
                R.string.privacy_section2_4_p1,
                R.string.privacy_section2_4_p2,
                R.string.privacy_section2_4_p3,
                R.string.privacy_section2_4_p4
            )
        ),
        DocSection(
            titleRes = R.string.privacy_section3_title,
            bodyRes = listOf(R.string.privacy_section3_p1, R.string.privacy_section3_p2)
        ),
        DocSection(
            titleRes = R.string.privacy_section4_title,
            bodyRes = listOf(
                R.string.privacy_section4_p1,
                R.string.privacy_section4_p2,
                R.string.privacy_section4_p3
            )
        ),
        DocSection(
            titleRes = R.string.privacy_section5_title,
            bodyRes = listOf(
                R.string.privacy_section5_p1,
                R.string.privacy_section5_p2,
                R.string.privacy_section5_p3
            )
        ),
        DocSection(
            titleRes = R.string.privacy_section6_title,
            bodyRes = listOf(
                R.string.privacy_section6_p1,
                R.string.privacy_section6_p2,
                R.string.privacy_section6_p3
            )
        ),
        DocSection(
            titleRes = R.string.privacy_section7_title,
            bodyRes = listOf(R.string.privacy_section7_p1, R.string.privacy_section7_p2)
        ),
        DocSection(
            titleRes = R.string.privacy_section8_title,
            bodyRes = listOf(
                R.string.privacy_section8_p1,
                R.string.privacy_section8_p2,
                R.string.privacy_section8_p3
            )
        ),
        DocSection(
            titleRes = R.string.privacy_section9_title,
            bodyRes = listOf(R.string.privacy_section9_p1)
        ),
        DocSection(
            titleRes = R.string.privacy_section10_title,
            bodyRes = listOf(R.string.privacy_section10_p1)
        )
    )

    LegalDocumentScreen(
        title = stringResource(R.string.about_privacy),
        sections = sections,
        onBack = onBack,
        modifier = modifier,
        header = {
            Text(
                text = stringResource(R.string.privacy_effective_date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    )
}
