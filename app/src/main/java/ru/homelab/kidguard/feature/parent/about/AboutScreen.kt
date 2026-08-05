package ru.homelab.kidguard.feature.parent.about

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import ru.homelab.kidguard.BuildConfig
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassCard

/**
 * Экран «О приложении»: иконка и назначение приложения, справочные документы (руководство,
 * политика, соглашение) и блок поддержки автора. «Скоро»-кнопки (оценить/поделиться/донат) видны,
 * но приглушены — ждут публикации в RuStore; по нажатию всё равно показывают Snackbar, а не молчат.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val versionName = BuildConfig.VERSION_NAME

    val soonSnackText = stringResource(R.string.about_soon_snack)
    val noMailClientText = stringResource(R.string.about_feedback_no_client)
    val feedbackSubject = stringResource(R.string.about_feedback_subject, versionName)
    val feedbackBody = stringResource(
        R.string.about_feedback_body,
        versionName,
        Build.MANUFACTURER,
        Build.MODEL,
        Build.VERSION.RELEASE
    )

    val onSoonClick: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar(soonSnackText) }
    }
    val onFeedbackClick: () -> Unit = {
        // На части устройств почтового клиента может не быть вовсе — тогда startActivity падает
        // с ActivityNotFoundException, и вместо краша показываем Snackbar с пояснением.
        val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
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
            CompactTopBar(title = stringResource(R.string.about_title), onBack = onBack)

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    // Берём иконку у системы, а не рисуем mipmap напрямую: ic_launcher_foreground —
                    // это СЛОЙ адаптивной иконки, по краям у него прозрачные поля под маску, и при
                    // прямом показе получался «квадрат в квадрате» (собственное скругление картинки
                    // внутри нашего clip). PackageManager отдаёт уже собранную иконку — ровно ту,
                    // что пользователь видит в лаунчере.
                    val appIcon = remember(context) {
                        runCatching {
                            context.packageManager
                                .getApplicationIcon(context.packageName)
                                .toBitmap(width = ICON_PX, height = ICON_PX)
                                .asImageBitmap()
                        }.getOrNull()
                    }
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier.size(96.dp)
                        )
                    } else {
                        // Фоллбэк на логотип-щит: экран «О приложении» не должен зиять пустотой,
                        // если иконку почему-то не удалось получить.
                        Image(
                            painter = painterResource(R.drawable.ic_shield_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        text = stringResource(R.string.about_slogan),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.about_version, versionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AboutSectionHeader(R.string.about_section_help)
                AboutRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.about_guide),
                    enabled = true,
                    badge = null,
                    onClick = onOpenGuide
                )
                AboutRow(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.about_privacy),
                    enabled = true,
                    badge = null,
                    onClick = onOpenPrivacy
                )
                AboutRow(
                    icon = Icons.Filled.CheckCircle,
                    title = stringResource(R.string.about_terms),
                    enabled = true,
                    badge = null,
                    onClick = onOpenTerms
                )

                AboutSectionHeader(R.string.about_section_support)
                val soonBadge = stringResource(R.string.about_badge_soon)
                AboutRow(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.about_rate),
                    enabled = false,
                    badge = soonBadge,
                    onClick = onSoonClick
                )
                AboutRow(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.about_share),
                    enabled = false,
                    badge = soonBadge,
                    onClick = onSoonClick
                )
                AboutRow(
                    icon = Icons.Filled.Email,
                    title = stringResource(R.string.about_feedback),
                    enabled = true,
                    badge = null,
                    onClick = onFeedbackClick
                )
                AboutRow(
                    icon = Icons.Filled.Favorite,
                    title = stringResource(R.string.about_donate),
                    enabled = true,
                    badge = null,
                    onClick = onOpenSupport
                )

                Text(
                    text = stringResource(R.string.about_soon_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
                )
            }
        }
    }
}

/** Заголовок секции — как `RuleSectionHeader`/`AccountSectionHeader` (uppercase, bold, разрядка). */
@Composable
private fun AboutSectionHeader(@StringRes text: Int) {
    Text(
        text = stringResource(text).uppercase(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

/**
 * Строка-карточка в списке справки/поддержки. Неактивные (ещё не готовые) пункты рисуются
 * приглушённо и с бейджем «скоро» вместо шеврона, но клик всё равно работает — по нему
 * показывается Snackbar с пояснением, а не тишина.
 */
@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .alpha(if (enabled) 1f else 0.45f)
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
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Размер, до которого растеризуем иконку приложения: 288px = 96dp на xxxhdpi, чтобы картинка
 * оставалась чёткой на плотных экранах.
 */
private const val ICON_PX = 288
