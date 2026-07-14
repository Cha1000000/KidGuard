package ru.homelab.kidguard.feature.parent.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.PinPad

private const val PIN_LENGTH = 4

/** Шаг мастера установки/смены PIN (веха 6.1): сперва придумать, потом повторить для проверки. */
private enum class WizardStep { ENTER, CONFIRM }

/**
 * Экран «PIN-защита» (веха 6.1, макет `docs/ui-concepts/veha-6-pin/pin-mockup.png`, экраны 1-2).
 * Если PIN не задан — сразу мастер ввода. Если задан — карточка статуса с «Сменить»/«Убрать».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PinSetupViewModel = hiltViewModel()
) {
    val pinProtection by viewModel.pinProtection.collectAsStateWithLifecycle()
    // Мастер ввода активен либо когда PIN ещё не задан, либо когда пользователь нажал «Сменить PIN».
    var wizardActive by rememberSaveable { mutableStateOf(false) }
    val showWizard = wizardActive || pinProtection == null

    Column(modifier = modifier) {
        TopAppBar(
            title = { Text(stringResource(R.string.pin_setup_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back)
                    )
                }
            }
        )
        if (showWizard) {
            PinWizard(
                onCompleted = { pin ->
                    viewModel.setPin(pin)
                    wizardActive = false
                }
            )
        } else {
            PinActiveState(
                onChangeClick = { wizardActive = true },
                onRemoveClick = { viewModel.clearPin() }
            )
        }
    }
}

@Composable
private fun PinWizard(onCompleted: (pin: String) -> Unit, modifier: Modifier = Modifier) {
    var step by remember { mutableStateOf(WizardStep.ENTER) }
    var firstPin by remember { mutableStateOf("") }
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun reset() {
        step = WizardStep.ENTER
        firstPin = ""
        enteredPin = ""
        isError = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.pin_setup_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp)
        )
        Text(
            text = stringResource(
                if (isError) R.string.pin_setup_mismatch
                else if (step == WizardStep.ENTER) R.string.pin_setup_enter
                else R.string.pin_setup_confirm
            ),
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        PinPad(
            enteredLength = enteredPin.length,
            maxLength = PIN_LENGTH,
            isError = isError,
            onDigit = { digit ->
                if (isError) isError = false
                if (enteredPin.length < PIN_LENGTH) enteredPin += digit
            },
            onBackspace = {
                if (isError) isError = false
                if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
            }
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                when (step) {
                    WizardStep.ENTER -> {
                        firstPin = enteredPin
                        enteredPin = ""
                        step = WizardStep.CONFIRM
                    }
                    WizardStep.CONFIRM -> {
                        if (enteredPin == firstPin) {
                            onCompleted(enteredPin)
                        } else {
                            isError = true
                            enteredPin = ""
                        }
                    }
                }
            },
            enabled = enteredPin.length == PIN_LENGTH,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Text(stringResource(R.string.common_save))
        }
    }

    // Ошибка «не совпало» — сброс мастера на первый шаг, чтобы пользователь начал заново.
    LaunchedEffect(isError) {
        if (isError) {
            delay(1200)
            reset()
        }
    }
}

@Composable
private fun PinActiveState(
    onChangeClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = stringResource(R.string.pin_setup_active_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.pin_setup_active_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        Button(onClick = onChangeClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pin_setup_change))
        }
        OutlinedButton(
            onClick = onRemoveClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text(stringResource(R.string.pin_setup_remove), color = MaterialTheme.colorScheme.error)
        }
    }
}
