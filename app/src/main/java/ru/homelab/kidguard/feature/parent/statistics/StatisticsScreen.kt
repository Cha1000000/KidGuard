package ru.homelab.kidguard.feature.parent.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.DailyBudgetState
import ru.homelab.kidguard.core.domain.model.dailyBudgetState
import ru.homelab.kidguard.core.ui.components.EmptyState
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.GlassDockBarReservedHeight
import ru.homelab.kidguard.core.ui.components.ScreenTitle
import ru.homelab.kidguard.core.ui.components.AppIconImage
import ru.homelab.kidguard.feature.parent.ChildSelectorChip
import ru.homelab.kidguard.feature.parent.ParentMenu
import java.time.format.TextStyle
import java.util.Locale

/** Вкладка «Статистика» родителя (веха 4.4): экранное время ребёнка с сервера. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onOpenAbout: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Обновляем при каждом входе на вкладку: VM переживает переключение вкладок
    // и без этого показывала бы устаревшие данные.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenTitle(
            stringResource(R.string.parent_tab_statistics),
            actions = { ParentMenu(onOpenAbout = onOpenAbout, onOpenAccount = onOpenAccount) }
        )
        if (!uiState.noChildren) ChildSelectorChip()

        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = uiState.refreshing,
            onRefresh = viewModel::refresh,
            state = pullToRefreshState,
            // Дефолтный индикатор красится в onSurfaceVariant (серый) — не совпадает с бирюзовым
            // акцентом приложения, которым красится обычный CircularProgressIndicator() без tint.
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = uiState.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            when {
                uiState.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                uiState.noChildren -> EmptyState(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.statistics_no_children),
                    modifier = Modifier.fillMaxSize()
                )

                uiState.error -> EmptyState(
                    icon = Icons.Filled.Warning,
                    title = stringResource(R.string.statistics_load_error),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                )

                else -> StatisticsContent(uiState)
            }
        }
    }
}

@Composable
private fun StatisticsContent(state: StatisticsUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            // Резерв снизу — плавающий GlassDockBar лежит поверх этого экрана.
            .padding(bottom = GlassDockBarReservedHeight)
    ) {
        if (!state.hasData) {
            EmptyState(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.statistics_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp)
            )
            return@Column
        }

        TodayCard(state)
        WeekChartCard(state.week)

        if (state.apps.isNotEmpty()) {
            Text(
                text = stringResource(R.string.statistics_apps_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )

            // Приложения с наигрышем < 2 минут по умолчанию скрыты — это шум
            // (случайно открытые/системные приложения), а не то, что интересует родителя.
            val (visibleApps, shortApps) = state.apps.partition { it.seconds >= MIN_VISIBLE_APP_SECONDS }
            // По умолчанию свёрнуто; сбрасывается при пересоздании экрана (смена ребёнка).
            var showAllApps by rememberSaveable { mutableStateOf(false) }
            val shown = if (showAllApps) state.apps else visibleApps

            shown.forEach { app -> AppUsageRow(app) }

            if (shortApps.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showAllApps = !showAllApps },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = if (showAllApps) {
                            stringResource(R.string.statistics_apps_show_less)
                        } else {
                            stringResource(R.string.statistics_apps_show_all, shortApps.size)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayCard(state: StatisticsUiState) {
    // Бюджет = лимит + бонус: та же формула, что у enforcement (ObserveLimitStateUseCase).
    // Раньше карточка показывала голый лимит и писала «исчерпан» при незаблокированном телефоне.
    val budget = dailyBudgetState(
        limitMinutes = state.todayLimitMinutes,
        bonusMinutes = state.todayBonusMinutes,
        usedMinutes = state.todaySeconds / 60
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.statistics_today_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Крупная цифра — всё экранное время: она совпадает с суммой блока
                    // «По приложениям». С бюджетом ниже сравнивается только та часть, что
                    // расходует лимит.
                    Text(
                        text = formatMinutes(state.todayTotalSeconds / 60),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // Иконка часов справа (как на мокапе)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_timer),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            when (budget) {
                DailyBudgetState.NoLimit -> Text(
                    text = stringResource(R.string.statistics_budget_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )

                is DailyBudgetState.Remaining -> {
                    BudgetTrack(
                        usedWithinBudgetMinutes = budget.usedMinutes,
                        overMinutes = 0,
                        budgetMinutes = budget.budgetMinutes
                    )
                    BudgetLine(budget.budgetMinutes, state.todayLimitMinutes, state.todayBonusMinutes)
                    Text(
                        text = stringResource(R.string.statistics_budget_left, formatMinutes(budget.leftMinutes)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                is DailyBudgetState.Overrun -> {
                    BudgetTrack(
                        usedWithinBudgetMinutes = budget.usedMinutes - budget.overMinutes,
                        overMinutes = budget.overMinutes,
                        budgetMinutes = budget.budgetMinutes
                    )
                    BudgetLine(budget.budgetMinutes, state.todayLimitMinutes, state.todayBonusMinutes)
                    OverrunLine(budget.overMinutes)
                }
            }

            OutsideLimitLine(state.outsideLimitSeconds / 60)
        }
    }
}

/**
 * «Вне лимита 1 ч 33 мин — «Всегда доступные» и домашний экран». Без этой строки крупная цифра
 * не сходилась бы с бюджетом и родитель не понимал бы, куда делась разница.
 */
@Composable
private fun OutsideLimitLine(minutes: Int) {
    if (minutes <= 0) return
    Text(
        text = buildAnnotatedString {
            append(stringResource(R.string.statistics_outside_limit, formatMinutes(minutes)))
            append(stringResource(R.string.statistics_outside_limit_hint))
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/**
 * Шкала расхода: бирюзовая часть — в пределах бюджета, золотая — сверх него, остаток — фон.
 * Общая длина считается по `max(расход, бюджет)`, поэтому при перерасходе полоска заполнена
 * целиком, а доля золотого показывает, насколько ребёнок вышел за бюджет.
 */
@Composable
private fun BudgetTrack(
    usedWithinBudgetMinutes: Int,
    overMinutes: Int,
    budgetMinutes: Int
) {
    val totalMinutes = maxOf(usedWithinBudgetMinutes + overMinutes, budgetMinutes)
    val restMinutes = totalMinutes - usedWithinBudgetMinutes - overMinutes

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        // weight() требует строго положительного значения — нулевые сегменты просто не рисуем.
        if (usedWithinBudgetMinutes > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(usedWithinBudgetMinutes.toFloat())
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        if (overMinutes > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(overMinutes.toFloat())
                    .background(MaterialTheme.colorScheme.tertiary)
            )
        }
        if (restMinutes > 0) {
            Spacer(modifier = Modifier.weight(restMinutes.toFloat()))
        }
    }
}

/**
 * «Бюджет дня 4 ч · 3 ч лимит + 1 ч бонус». Расшифровку показываем только при бонусе.
 * Одним [Text], а не двумя в [Row]: при длинных значениях («3 ч 30 мин лимит + 45 мин бонус»)
 * второй Text переносился бы целиком, а так перенос идёт по словам.
 */
@Composable
private fun BudgetLine(budgetMinutes: Int, limitMinutes: Int?, bonusMinutes: Int) {
    val detail = when {
        bonusMinutes > 0 && limitMinutes != null -> stringResource(
            R.string.statistics_budget_formula,
            formatMinutes(limitMinutes),
            formatMinutes(bonusMinutes)
        )
        // Лимит 0 без бонуса: «Бюджет дня 0 мин» сам по себе выглядит как сбой загрузки.
        budgetMinutes == 0 -> stringResource(R.string.statistics_budget_closed)
        else -> null
    }
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(stringResource(R.string.statistics_budget, formatMinutes(budgetMinutes)))
            }
            if (detail != null) {
                withStyle(SpanStyle(color = hintColor)) { append(detail) }
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 10.dp)
    )
}

/**
 * «Сверх бюджета 53 мин — доступны только разрешённые приложения». Пояснение обязательно: без него
 * родитель видит расход больше бюджета и решает, что блокировка не сработала. На деле обычные
 * приложения перекрыты, а время продолжает капать на «Всегда доступных» и лаунчере — оверлей
 * блокировки смахиваемый намеренно.
 */
@Composable
private fun OverrunLine(overMinutes: Int) {
    val overColor = MaterialTheme.colorScheme.tertiary
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val text = if (overMinutes > 0) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = overColor, fontWeight = FontWeight.SemiBold)) {
                append(stringResource(R.string.statistics_budget_over, formatMinutes(overMinutes)))
            }
            withStyle(SpanStyle(color = hintColor)) {
                append(stringResource(R.string.statistics_budget_over_hint))
            }
        }
    } else {
        // Ровно по бюджету: перерасхода ещё нет, но время уже вышло.
        buildAnnotatedString {
            withStyle(SpanStyle(color = overColor, fontWeight = FontWeight.SemiBold)) {
                append(stringResource(R.string.statistics_budget_spent))
            }
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun WeekChartCard(week: List<DayUsage>) {
    // Масштаб — по максимуму из факта и бюджетов: иначе в день, когда ребёнок не выбрал лимит,
    // риска бюджета ушла бы выше верха графика.
    val maxSeconds = maxOf(
        week.maxOfOrNull { it.seconds } ?: 0,
        week.maxOfOrNull { (it.budgetMinutes ?: 0) * 60 } ?: 0
    ).coerceAtLeast(1)
    val lastIndex = week.lastIndex
    val budgetLineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.statistics_week_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            // Риски бюджета — отдельным слоем ПОД столбиками: так пунктир не перечёркивает
            // подписи значений, а там, где столбик выше бюджета, уровень и так виден по границе
            // бирюзового и золотого. Линия рисуется на весь ряд, а не внутри каждого столбика:
            // дни с одинаковым бюджетом сливаются в одну сплошную линию, а при разных лимитах
            // по дням недели получается видимая ступенька.
            Box(modifier = Modifier.fillMaxWidth()) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawBudgetLines(week, maxSeconds, budgetLineColor)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(BarGap),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                week.forEachIndexed { index, day ->
                    // Лимита в этот день не было — перерасхода тоже нет, весь столбик обычный.
                    // Через `?: 0` получалось бы, что бюджет нулевой и день целиком «сверх».
                    val overSeconds = day.budgetMinutes
                        ?.let { (day.seconds - it * 60).coerceAtLeast(0) }
                        ?: 0
                    val withinSeconds = day.seconds - overSeconds
                    val isToday = index == lastIndex
                    // Прошлые дни приглушены, сегодня — в полную силу (так было и раньше).
                    val alpha = if (isToday) 1f else 0.45f

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Высота области фиксирована, а не «сколько останется от подписи дня»:
                        // риски бюджета рисуются по этой же величине, и при системном увеличении
                        // шрифта они иначе разъехались бы со столбиками.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(BarAreaHeight)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                if (day.seconds > 0) {
                                    Text(
                                        text = formatChartValue(day.seconds),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .fillMaxWidth()
                                        // Минимум 2dp, чтобы нулевые дни были видны линией у основания.
                                        .height(
                                            (MaxBarHeight.value * day.seconds / maxSeconds).dp
                                                .coerceAtLeast(2.dp)
                                        )
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                ) {
                                    if (overSeconds > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(overSeconds.toFloat())
                                                .background(
                                                    MaterialTheme.colorScheme.tertiary.copy(alpha = alpha)
                                                )
                                        )
                                    }
                                    if (withinSeconds > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(withinSeconds.toFloat())
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                                )
                                        )
                                    }
                                    // День без использования: полоска-минимум у основания.
                                    if (day.seconds == 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = day.date.dayOfWeek
                                .getDisplayName(TextStyle.SHORT, Locale("ru"))
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        }
                    }
                }
            }
            ChartLegend()
        }
    }
}

/**
 * Пунктирные риски бюджета по дням. Считает геометрию сама: ширина ячейки выводится из ширины
 * ряда и [BarGap], а высота — из [MaxBarHeight], того же значения, по которому масштабируются
 * столбики. Основание столбиков — низ [BarAreaHeight], под ней идёт подпись дня недели.
 */
private fun DrawScope.drawBudgetLines(
    week: List<DayUsage>,
    maxSeconds: Int,
    color: Color
) {
    if (week.isEmpty()) return
    val gapPx = BarGap.toPx()
    val cellWidth = (size.width - gapPx * (week.size - 1)) / week.size
    val maxBarPx = MaxBarHeight.toPx()
    // Основание столбиков — низ области фиксированной высоты, а не низ всего ряда: под ней ещё
    // подпись дня недели.
    val baseLineY = BarAreaHeight.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(DashOnPx, DashOffPx))

    week.forEachIndexed { index, day ->
        val budgetMinutes = day.budgetMinutes ?: return@forEachIndexed
        val y = baseLineY - maxBarPx * (budgetMinutes * 60f) / maxSeconds
        if (y < 0f) return@forEachIndexed
        val left = index * (cellWidth + gapPx)
        // В промежуток между столбиками риска заходит только к соседу с ТАКИМ ЖЕ бюджетом: тогда
        // дни с одинаковым лимитом сливаются в одну непрерывную линию, а день с бонусом не даёт
        // висящего в воздухе штриха на своём уровне.
        val startPad = if (week.getOrNull(index - 1)?.budgetMinutes == budgetMinutes) gapPx / 2 else 0f
        val endPad = if (week.getOrNull(index + 1)?.budgetMinutes == budgetMinutes) gapPx / 2 else 0f
        drawLine(
            color = color,
            start = Offset((left - startPad).coerceAtLeast(0f), y),
            end = Offset((left + cellWidth + endPad).coerceAtMost(size.width), y),
            strokeWidth = BudgetLineStroke.toPx(),
            pathEffect = dash
        )
    }
}

@Composable
private fun ChartLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(
            color = MaterialTheme.colorScheme.primary,
            label = stringResource(R.string.statistics_week_legend_within)
        )
        LegendItem(
            color = MaterialTheme.colorScheme.tertiary,
            label = stringResource(R.string.statistics_week_legend_over)
        )
        LegendItem(
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
            label = stringResource(R.string.statistics_week_legend_budget),
            dashed = true
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dashed: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dashed) {
            Canvas(modifier = Modifier.size(width = 14.dp, height = 8.dp)) {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = BudgetLineStroke.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOnPx, DashOffPx))
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppUsageRow(app: AppUsage) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconImage(icon = app.icon, label = app.label, packageName = app.packageName)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatMinutes(app.seconds / 60),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        // Полоска — доля приложения в суммарном времени за день.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(app.share)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun formatMinutes(minutes: Int): String = when {
    // Целые часы — без «00 мин»: строка бюджета с расшифровкой иначе не влезает в ширину экрана.
    minutes >= 60 && minutes % 60 == 0 -> stringResource(R.string.limit_value_h, minutes / 60)
    minutes >= 60 -> stringResource(R.string.limit_value_hm, minutes / 60, minutes % 60)
    else -> stringResource(R.string.limit_value_m, minutes)
}

/** Компактное «1:05» (ч:мм) для подписи над столбиком диаграммы. */
private fun formatChartValue(seconds: Int): String {
    val minutes = seconds / 60
    return "%d:%02d".format(minutes / 60, minutes % 60)
}

// 2 минуты — отсекаем шум из случайно открытых/системных приложений в списке «По приложениям».
private const val MIN_VISIBLE_APP_SECONDS = 120

// Геометрия диаграммы. Столбики масштабируются по MaxBarHeight, а риски бюджета рисуются по тем же
// величинам — держим их рядом, чтобы правка одной не разъехалась с другой.
private val BarGap = 10.dp
private val MaxBarHeight = 110.dp
/** Столбик + подпись значения над ним. Фиксирована: от неё считается основание столбиков. */
private val BarAreaHeight = 128.dp
private val BudgetLineStroke = 2.dp
private const val DashOnPx = 6f
private const val DashOffPx = 6f
