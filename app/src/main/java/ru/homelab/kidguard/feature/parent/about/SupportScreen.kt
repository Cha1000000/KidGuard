package ru.homelab.kidguard.feature.parent.about

import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import ru.homelab.kidguard.BuildConfig
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassCard

/** Страница приёма поддержки. Обезличенный идентификатор CloudTips — личные реквизиты автора в APK не попадают. */
private const val SUPPORT_URL = "https://pay.cloudtips.ru/p/a8b6710d"

/**
 * Экран «Поддержать проект»: статическая витрина без ViewModel — весь контент фиксированный,
 * состояние (кроме Snackbar) не нужно. Оплата уходит на внешнюю страницу CloudTips: встроенные
 * платежи RuStore физлицу недоступны, поэтому суммы — это просто ссылки с параметром amount.
 */
@Composable
fun SupportScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val versionName = BuildConfig.VERSION_NAME

    val soonSnackText = stringResource(R.string.about_soon_snack)
    val linkCopiedText = stringResource(R.string.support_link_copied)
    val openErrorText = stringResource(R.string.support_open_error)
    val noMailClientText = stringResource(R.string.about_feedback_no_client)
    val feedbackSubject = stringResource(R.string.about_feedback_subject, versionName)
    val feedbackBody = stringResource(
        R.string.about_feedback_body,
        versionName,
        Build.MANUFACTURER,
        Build.MODEL,
        Build.VERSION.RELEASE
    )

    // Открытие внешней страницы оплаты. На части устройств (или без интернета) ACTION_VIEW может
    // не найти обработчик/упасть — тогда вместо краша показываем Snackbar с пояснением.
    val openUrl: (String) -> Unit = { url ->
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { context.startActivity(intent) }.isSuccess
        if (!launched) {
            scope.launch { snackbarHostState.showSnackbar(openErrorText) }
        }
    }
    val onSoonClick: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar(soonSnackText) }
    }
    val onCopyLinkClick: () -> Unit = {
        clipboard.setText(AnnotatedString(SUPPORT_URL))
        scope.launch { snackbarHostState.showSnackbar(linkCopiedText) }
    }
    val onFeedbackClick: () -> Unit = {
        // Та же логика, что и «Обратная связь» в AboutScreen: без почтового клиента startActivity
        // падает с ActivityNotFoundException, ловим через runCatching и показываем Snackbar.
        val mailIntent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf("racerkafa@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
            putExtra(Intent.EXTRA_TEXT, feedbackBody)
        }
        val launched = runCatching { context.startActivity(mailIntent) }.isSuccess
        if (!launched) {
            scope.launch { snackbarHostState.showSnackbar(noMailClientText) }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        // Отступы под системные панели уже расставлены выше по дереву (safeDrawing в ParentScreen)
        // и внутри CompactTopBar (statusBarsPadding) — здесь свои не нужны, иначе поедет вёрстка.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            CompactTopBar(title = stringResource(R.string.support_title), onBack = onBack)

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Шапка: копилка, заголовок и пояснение — всё по центру
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    // Именно Image, а не Icon: Icon красит содержимое одним tint'ом и убил бы
                    // градиенты копилки. Круглая подложка не нужна — иконка сама цветная,
                    // фон под ней только спорил бы с ней за внимание.
                    Image(
                        painter = painterResource(R.drawable.ic_support_piggy),
                        contentDescription = null,
                        modifier = Modifier.size(76.dp)
                    )
                    Text(
                        text = stringResource(R.string.support_hero_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        text = stringResource(R.string.support_hero_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }

                // Карточка «На что расходуются средства»
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.support_spend_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        SupportSpendItem(stringResource(R.string.support_spend_server))
                        SupportSpendItem(stringResource(R.string.support_spend_domain))
                        SupportSpendItem(stringResource(R.string.support_spend_dev))
                    }
                }

                SupportSectionHeader(R.string.support_section_once)

                // Сетка 2×2: 100 / 300 (акцент, «чаще всего») / 500 / своя сумма
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SupportAmountButton(
                            label = stringResource(R.string.support_amount_100),
                            accent = false,
                            modifier = Modifier.weight(1f),
                            onClick = { openUrl("$SUPPORT_URL?amount=100") }
                        )
                        SupportAmountButton(
                            label = stringResource(R.string.support_amount_300),
                            caption = stringResource(R.string.support_amount_popular),
                            accent = true,
                            modifier = Modifier.weight(1f),
                            onClick = { openUrl("$SUPPORT_URL?amount=300") }
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 9.dp)
                    ) {
                        SupportAmountButton(
                            label = stringResource(R.string.support_amount_500),
                            accent = false,
                            modifier = Modifier.weight(1f),
                            onClick = { openUrl("$SUPPORT_URL?amount=500") }
                        )
                        SupportAmountButton(
                            label = stringResource(R.string.support_amount_custom),
                            accent = false,
                            modifier = Modifier.weight(1f),
                            onClick = { openUrl(SUPPORT_URL) }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.support_secure_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )

                SupportRow(
                    icon = ImageVector.vectorResource(R.drawable.ic_link),
                    title = stringResource(R.string.support_copy_link),
                    onClick = onCopyLinkClick,
                    modifier = Modifier.padding(top = 16.dp)
                )

                SupportSectionHeader(R.string.support_section_free)
                SupportRow(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.support_rate),
                    onClick = onSoonClick
                )
                SupportRow(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.support_share),
                    onClick = onSoonClick
                )
                SupportRow(
                    icon = Icons.Filled.Email,
                    title = stringResource(R.string.support_feedback),
                    onClick = onFeedbackClick
                )

                Text(
                    text = stringResource(R.string.support_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 24.dp)
                )
            }
        }
    }
}

/** Заголовок секции — как `AccountSectionHeader`/`AboutSectionHeader` (uppercase, bold, разрядка). */
@Composable
private fun SupportSectionHeader(@StringRes text: Int) {
    Text(
        text = stringResource(text).uppercase(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

/** Строка-пункт «на что идут деньги»: маркер «• » + подпись. */
@Composable
private fun SupportSpendItem(text: String) {
    Row(modifier = Modifier.padding(bottom = 3.dp)) {
        Text(
            text = "• ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Кнопка суммы в сетке 2×2. Акцентная (300 ₽) — заливка `primary` и опциональная подпись
 * («чаще всего») под номиналом; остальные — обычная `GlassCard`-кнопка.
 */
@Composable
private fun SupportAmountButton(
    label: String,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    val shape = RoundedCornerShape(14.dp)
    if (accent) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                if (caption != null) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    } else {
        GlassCard(
            onClick = onClick,
            cornerRadius = 14.dp,
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp),
            modifier = modifier
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Строка-кнопка списка (копирование ссылки / бесплатная поддержка) — по образцу `AboutRow`. */
@Composable
private fun SupportRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
