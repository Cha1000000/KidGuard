package ru.homelab.kidguard.feature.auth

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import timber.log.Timber

/**
 * Тонкая обёртка над Credential Manager (Sign in with Google). Живёт в `:app`, а не в `:data`/
 * `:core`, потому что `getCredential()` требует Activity-контекст для показа UI — держать его в
 * Hilt-синглтоне (ViewModel/Repository) было бы утечкой. Вызывается напрямую из Composable
 * с `LocalContext.current`, результат (ID-token) передаётся во ViewModel уже как обычная строка.
 */
object GoogleSignInLauncher {

    /**
     * Запрашивает Google ID-token.
     *
     * [filterByAuthorizedAccounts] = `true` — тихая попытка: только уже авторизованные на этом
     * устройстве аккаунты, без UI (для автообновления сессии в будущих шагах). `false` — обычный
     * интерактивный вход с выбором аккаунта (используется на экране входа).
     *
     * Возвращает `null`, если пользователь отменил или (при тихой попытке) подходящих аккаунтов нет.
     */
    suspend fun requestIdToken(context: Context, filterByAuthorizedAccounts: Boolean): String? {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(GOOGLE_WEB_CLIENT_ID)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = CredentialManager.create(context).getCredential(context, request)
            extractIdToken(response.credential)
        } catch (error: NoCredentialException) {
            // На устройстве нет ни одного подходящего Google-аккаунта (или для тихой попытки —
            // ни одного ранее авторизованного). Отдельный catch — по требованию lint-правила
            // CredentialManagerMisuse; UI-поведение то же самое (null -> общий экран ошибки).
            Timber.tag(TAG).i(
                "Нет подходящего Google-аккаунта (тихая попытка=%s)",
                filterByAuthorizedAccounts
            )
            null
        } catch (error: GetCredentialException) {
            Timber.tag(TAG).w(
                error,
                "Google Sign-In не выполнен (тихая попытка=%s)",
                filterByAuthorizedAccounts
            )
            null
        }
    }

    private fun extractIdToken(credential: Credential): String? {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return null
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    // Web OAuth client ID из Google Cloud (проект kidguard-501811) — тот же, что GOOGLE_CLIENT_ID
    // на сервере (KidGuard-server/.env). Публичный идентификатор, не секрет: Google выдаёт
    // ID-token с этим audience, сервер потом сверяет его при верификации.
    private const val GOOGLE_WEB_CLIENT_ID =
        "341022654520-njuh1kmj7a4k3pq5opd5m2a5buh6drs7.apps.googleusercontent.com"

    private const val TAG = "GoogleSignIn"
}
