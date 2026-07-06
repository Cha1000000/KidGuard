package ru.homelab.kidguard.feature.parent

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.vector.ImageVector
import ru.homelab.kidguard.R

/** Вкладки нижней навигации родительского режима. */
enum class ParentTab(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
) {
    CHILDREN("parent/children", R.string.parent_tab_children, Icons.Filled.Face),
    RULES("parent/rules", R.string.parent_tab_rules, Icons.Filled.Lock),
    STATISTICS("parent/statistics", R.string.parent_tab_statistics, Icons.Filled.DateRange)
}
