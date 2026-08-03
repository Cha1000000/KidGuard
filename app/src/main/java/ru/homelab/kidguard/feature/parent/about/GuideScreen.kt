package ru.homelab.kidguard.feature.parent.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.homelab.kidguard.R

/**
 * Экран «Как пользоваться». Текст перенесён дословно из docs/guide/user-guide.md (согласованный
 * черновик, без служебной шапки про согласование) в строковые ресурсы guide_section*_title/p*.
 * Таблицы markdown превращены в текстовые перечисления с «• », как и в политике конфиденциальности.
 * Подразделы разбиты на отдельные DocSection по аналогии с privacy_section2_1 и т.п.
 *
 * Три иллюстрации из drawable-nodpi встроены прямо в текст: мастер разрешений — в разделе 2,
 * экран ребёнка — в разделе 8, плашка о поломке контроля — в подразделе про красную плашку
 * раздела 11.
 */
@Composable
fun GuideScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sections = listOf(
        DocSection(
            titleRes = R.string.guide_section1_title,
            bodyRes = listOf(R.string.guide_section1_p1, R.string.guide_section1_p2)
        ),
        DocSection(
            titleRes = R.string.guide_section1_sub1_title,
            bodyRes = listOf(R.string.guide_section1_sub1_p1)
        ),
        DocSection(
            titleRes = R.string.guide_section1_sub2_title,
            bodyRes = listOf(R.string.guide_section1_sub2_p1, R.string.guide_section1_sub2_p2)
        ),
        DocSection(
            titleRes = R.string.guide_section1_sub3_title,
            bodyRes = listOf(R.string.guide_section1_sub3_p1, R.string.guide_section1_sub3_p2)
        ),

        DocSection(
            titleRes = R.string.guide_section2_title,
            bodyRes = listOf(R.string.guide_section2_p1),
            imageRes = R.drawable.guide_permissions,
            imageCaptionRes = R.string.guide_img_permissions_caption
        ),
        DocSection(
            titleRes = R.string.guide_section2_sub1_title,
            bodyRes = listOf(R.string.guide_section2_sub1_p1)
        ),
        DocSection(
            titleRes = R.string.guide_section2_sub2_title,
            bodyRes = listOf(R.string.guide_section2_sub2_p1, R.string.guide_section2_sub2_p2)
        ),
        DocSection(
            titleRes = R.string.guide_section2_sub3_title,
            bodyRes = listOf(R.string.guide_section2_sub3_p1)
        ),

        DocSection(
            titleRes = R.string.guide_section3_title,
            bodyRes = listOf(R.string.guide_section3_p1)
        ),
        DocSection(
            titleRes = R.string.guide_section3_sub1_title,
            bodyRes = listOf(
                R.string.guide_section3_sub1_p1,
                R.string.guide_section3_sub1_p2,
                R.string.guide_section3_sub1_p3,
                R.string.guide_section3_sub1_p4
            )
        ),
        DocSection(
            titleRes = R.string.guide_section3_sub2_title,
            bodyRes = listOf(R.string.guide_section3_sub2_p1)
        ),
        DocSection(
            titleRes = R.string.guide_section3_sub3_title,
            bodyRes = listOf(R.string.guide_section3_sub3_p1, R.string.guide_section3_sub3_p2)
        ),

        DocSection(
            titleRes = R.string.guide_section4_title,
            bodyRes = listOf(R.string.guide_section4_p1)
        ),
        DocSection(
            titleRes = R.string.guide_section4_sub1_title,
            bodyRes = listOf(R.string.guide_section4_sub1_p1)
        ),
        DocSection(
            titleRes = R.string.guide_section4_sub2_title,
            bodyRes = listOf(R.string.guide_section4_sub2_p1)
        ),
        DocSection(
            titleRes = R.string.guide_section4_sub3_title,
            bodyRes = listOf(R.string.guide_section4_sub3_p1, R.string.guide_section4_sub3_p2)
        ),

        DocSection(
            titleRes = R.string.guide_section5_title,
            bodyRes = listOf(
                R.string.guide_section5_p1,
                R.string.guide_section5_p2,
                R.string.guide_section5_p3,
                R.string.guide_section5_p4
            )
        ),

        DocSection(titleRes = R.string.guide_section6_title),
        DocSection(
            titleRes = R.string.guide_section6_sub1_title,
            bodyRes = listOf(R.string.guide_section6_sub1_p1)
        ),
        DocSection(
            titleRes = R.string.guide_section6_sub2_title,
            bodyRes = listOf(R.string.guide_section6_sub2_p1, R.string.guide_section6_sub2_p2)
        ),
        DocSection(
            titleRes = R.string.guide_section6_sub3_title,
            bodyRes = listOf(R.string.guide_section6_sub3_p1, R.string.guide_section6_sub3_p2)
        ),

        DocSection(
            titleRes = R.string.guide_section7_title,
            bodyRes = listOf(
                R.string.guide_section7_p1,
                R.string.guide_section7_p2,
                R.string.guide_section7_p3,
                R.string.guide_section7_p4
            )
        ),

        DocSection(
            titleRes = R.string.guide_section8_title,
            bodyRes = listOf(
                R.string.guide_section8_p1,
                R.string.guide_section8_p2,
                R.string.guide_section8_p3
            ),
            imageRes = R.drawable.guide_child_today,
            imageCaptionRes = R.string.guide_img_child_caption
        ),

        DocSection(
            titleRes = R.string.guide_section9_title,
            bodyRes = listOf(R.string.guide_section9_p1)
        ),

        DocSection(
            titleRes = R.string.guide_section10_title,
            bodyRes = listOf(R.string.guide_section10_p1, R.string.guide_section10_p2)
        ),

        DocSection(titleRes = R.string.guide_section11_title),
        DocSection(
            titleRes = R.string.guide_section11_sub1_title,
            bodyRes = listOf(R.string.guide_section11_sub1_p1, R.string.guide_section11_sub1_p2),
            imageRes = R.drawable.guide_health_warning,
            imageCaptionRes = R.string.guide_img_health_caption
        ),
        DocSection(
            titleRes = R.string.guide_section11_sub2_title,
            bodyRes = listOf(R.string.guide_section11_sub2_p1, R.string.guide_section11_sub2_p2)
        ),
        DocSection(
            titleRes = R.string.guide_section11_sub3_title,
            bodyRes = listOf(R.string.guide_section11_sub3_p1)
        ),
        DocSection(
            titleRes = R.string.guide_section11_sub4_title,
            bodyRes = listOf(R.string.guide_section11_sub4_p1, R.string.guide_section11_sub4_p2)
        ),

        DocSection(
            titleRes = R.string.guide_section12_title,
            bodyRes = listOf(R.string.guide_section12_p1)
        )
    )

    LegalDocumentScreen(
        title = stringResource(R.string.guide_title),
        sections = sections,
        onBack = onBack,
        modifier = modifier
    )
}
