package ru.homelab.kidguard.feature.child.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassBackground
import ru.homelab.kidguard.core.ui.components.PinPad

/**
 * PIN-гейт перед мастером разрешений в детском режиме (веха 6).
 *
 * Зачем PIN: экран статусов разрешений — карта дыр в защите. «Специальные возможности: не
 * выдано» читается ребёнком как «контроль не работает, можно гулять» (см. kdoc DeviceHealth).
 *
 * Это Compose-экран, а НЕ PinOverlayManager: оверлей рисуется через WindowManager
 * accessibility-сервиса, а этот экран нужен как раз тогда, когда accessibility сломан и сервис
 * мёртв.
 */
@Composable
fun ChildPinScreen(
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChildPinViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }

    GlassBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompactTopBar(title = stringResource(R.string.child_pin_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusText(state),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.isError || state.isBlocked) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 20.dp)
                )
                PinPad(
                    enteredLength = state.entered.length,
                    isError = state.isError,
                    onDigit = { if (!state.isBlocked) viewModel.onDigit(it) },
                    onBackspace = { if (!state.isBlocked) viewModel.onBackspace() }
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun statusText(state: ChildPinUiState): String = when {
    state.isBlocked -> stringResource(R.string.child_pin_blocked, formatCountdown(state.blockedSecondsLeft))
    state.attemptsLeft != null -> stringResource(R.string.child_pin_attempts_left, state.attemptsLeft)
    else -> stringResource(R.string.child_pin_prompt)
}

/** «0:47» — родителю нужен наглядный отсчёт, а не «47 секунд». */
private fun formatCountdown(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
