package ru.homelab.kidguard.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.Role

/**
 * Экран входа через Google — общий для обеих ролей, показывается один раз после выбора роли
 * в онбординге, а также при холодном старте, если сохранённая сессия истекла (см. [Destinations][ru.homelab.kidguard.core.ui.navigation.Destinations.LOGIN]).
 */
@Composable
fun SignInScreen(
    onSignedIn: (Role) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is SignInUiState.Success) {
            onSignedIn(state.role)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Theme.KidGuard (styles.xml) — фиксированная светлая системная тема (статичный
            // windowBackground); экраны без Scaffold (у него это уже встроено через
            // containerColor) должны красить фон сами, иначе в тёмной теме будет просвечивать
            // светлый фон окна.
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield_logo),
                    contentDescription = null,
                    // Unspecified — иконка двухцветная сама по себе (см. ic_shield_logo.xml),
                    // единый tint убрал бы внутренний блик.
                    tint = Color.Unspecified,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.signin_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = stringResource(R.string.signin_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 36.dp)
        )

        when (val state = uiState) {
            SignInUiState.Loading -> {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.signin_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            else -> {
                OutlinedButton(
                    onClick = {
                        viewModel.resetError()
                        scope.launch {
                            val idToken = GoogleSignInLauncher.requestIdToken(
                                context = context,
                                filterByAuthorizedAccounts = false
                            )
                            if (idToken != null) {
                                viewModel.signIn(idToken)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_google_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.signin_button),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.signin_consent_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp)
                )
                if (state is SignInUiState.Error) {
                    Text(
                        text = stringResource(R.string.signin_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}
