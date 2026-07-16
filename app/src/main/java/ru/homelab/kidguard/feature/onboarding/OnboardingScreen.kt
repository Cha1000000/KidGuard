package ru.homelab.kidguard.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.Role
import ru.homelab.kidguard.core.ui.components.GlassBackground

/**
 * Экран первичной настройки: выбор роли устройства. Выбор **единоразовый** — приложение
 * запомнит его навсегда, без повторного входа и смены роли.
 */
@Composable
fun OnboardingScreen(
    onRoleChosen: (Role) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    GlassBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 40.dp)
            )
            // Крупные, слабо скруглённые кнопки выбора роли (прямоугольные со скруглёнными углами).
            val roleButtonShape = RoundedCornerShape(16.dp)
            Button(
                onClick = { viewModel.chooseRole(Role.PARENT, onRoleChosen) },
                shape = roleButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_role_parent),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            OutlinedButton(
                onClick = { viewModel.chooseRole(Role.CHILD, onRoleChosen) },
                shape = roleButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(64.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_role_child),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}
