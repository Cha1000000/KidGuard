package ru.homelab.kidguard.feature.parent.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.ChildAlertRow
import ru.homelab.kidguard.core.domain.repository.AlertSettingsRepository
import ru.homelab.kidguard.core.domain.usecase.isValidAlertEmail
import ru.homelab.kidguard.core.domain.usecase.normalizeAlertEmail
import javax.inject.Inject

/** Что показать под полем адреса после сохранения. */
sealed interface EmailFeedback {
    /** Адрес принят, проверочное письмо ушло на него. */
    data class VerificationSent(val email: String) : EmailFeedback

    /** Адрес сохранён, но письмо отправить не удалось — обещать доставку нельзя. */
    data object VerificationFailed : EmailFeedback

    /** Поле очищено: письма вернулись на адрес аккаунта. */
    data object ResetToAccount : EmailFeedback
}

data class NotificationsUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val accountEmail: String = "",
    /** Текст в поле ввода. Заполнен адресом, на который письма уходят фактически. */
    val emailInput: String = "",
    /** Что сейчас сохранено на сервере; `null` — «слать на адрес аккаунта». */
    val savedAlertEmail: String? = null,
    val savingEmail: Boolean = false,
    val emailFeedback: EmailFeedback? = null,
    val children: List<ChildAlertRow> = emptyList(),
    val saveFailed: Boolean = false
) {
    /**
     * Введённое значение как оно ляжет на сервер. Адрес, совпавший с аккаунтным, — это `null`:
     * хранить его копией бессмысленно, а поведение у них одинаковое.
     */
    private val pendingEmail: String?
        get() = normalizeAlertEmail(emailInput)?.takeIf { !it.equals(accountEmail, ignoreCase = true) }

    /** Введено что-то непохожее на адрес — подсветить поле и не пускать в сохранение. */
    val emailInvalid: Boolean
        get() = emailInput.isNotBlank() && !isValidAlertEmail(emailInput)

    /** Кнопка активна, только когда есть что сохранять и оно осмысленно. */
    val canSaveEmail: Boolean
        get() = !savingEmail && !emailInvalid && pendingEmail != savedAlertEmail

    /** Указан адрес, отличный от аккаунтного, — стоит напомнить, к чему вернёт очистка поля. */
    val showAccountHint: Boolean get() = savedAlertEmail != null

    /** Ни одному ребёнку письма не идут — адрес сейчас ни на что не влияет. */
    val emailChannelUnused: Boolean
        get() = children.isNotEmpty() && children.none { it.prefs.email }

    internal fun emailToSave(): String? = pendingEmail
}

/**
 * Экран «Оповещения»: каким каналом звать родителя по каждому ребёнку и на какой адрес слать
 * письма.
 *
 * Настройки личные — это подписка ЭТОГО родителя, а не свойство ребёнка (у ребёнка бывает двое
 * равноправных родителей с общими правилами, но своими оповещениями). Поэтому экран живёт в меню
 * «три точки», рядом с «Аккаунтом», а не в карточке ребёнка среди правил.
 *
 * Активный ребёнок здесь не используется вовсе: показываем всех сразу. Переключение активного
 * стоит сетевого запроса (тянется политика) и меняет глобальное состояние остальных вкладок —
 * ради двух галок это неоправданно.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: AlertSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, loadFailed = false) }
            repository.load()
                .onSuccess { settings ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            accountEmail = settings.accountEmail,
                            savedAlertEmail = settings.alertEmail,
                            // В поле — фактический адрес доставки, а не пустота с плейсхолдером:
                            // родитель должен видеть, куда письма уходят СЕЙЧАС, и править это
                            // как обычный текст. Очистка поля возвращает на адрес аккаунта.
                            emailInput = settings.effectiveEmail,
                            children = settings.children,
                            emailFeedback = null
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(loading = false, loadFailed = true) } }
        }
    }

    fun onEmailChanged(value: String) {
        // Любая правка снимает прошлый вердикт: он относился к предыдущему адресу.
        _uiState.update { it.copy(emailInput = value, emailFeedback = null, saveFailed = false) }
    }

    fun clearEmail() = onEmailChanged("")

    fun saveEmail() {
        val state = _uiState.value
        if (!state.canSaveEmail) return
        val target = state.emailToSave()

        viewModelScope.launch {
            _uiState.update { it.copy(savingEmail = true, saveFailed = false, emailFeedback = null) }
            repository.setAlertEmail(target)
                .onSuccess { saved ->
                    // Локальная переменная, а не saved.alertEmail по месту: smart cast через
                    // границу модуля (:core) Kotlin не делает.
                    val savedEmail = saved.alertEmail
                    _uiState.update {
                        it.copy(
                            savingEmail = false,
                            savedAlertEmail = savedEmail,
                            emailInput = savedEmail ?: it.accountEmail,
                            emailFeedback = when {
                                savedEmail == null -> EmailFeedback.ResetToAccount
                                saved.verificationSent -> EmailFeedback.VerificationSent(savedEmail)
                                // Адрес сервер сохранил, но письмо не ушло. Молчать нельзя:
                                // родитель решит, что канал проверен, и будет ждать тревог,
                                // которых не увидит.
                                else -> EmailFeedback.VerificationFailed
                            }
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(savingEmail = false, saveFailed = true) } }
        }
    }

    fun setPush(childId: Int, enabled: Boolean) = setPrefs(childId, push = enabled)

    fun setEmail(childId: Int, enabled: Boolean) = setPrefs(childId, email = enabled)

    /**
     * Переключатель применяется оптимистично: тумблер должен отвечать на палец мгновенно, а не
     * через круг ожидания. Если сервер не принял — возвращаем прежнее значение и говорим об этом,
     * иначе экран показывал бы настройку, которой на сервере нет.
     */
    private fun setPrefs(childId: Int, push: Boolean? = null, email: Boolean? = null) {
        val previous = _uiState.value.children
        _uiState.update { state ->
            state.copy(
                saveFailed = false,
                children = state.children.map { row ->
                    if (row.child.id != childId) row else row.copy(
                        prefs = row.prefs.copy(
                            push = push ?: row.prefs.push,
                            email = email ?: row.prefs.email
                        )
                    )
                }
            )
        }

        viewModelScope.launch {
            repository.setChildPrefs(childId, push = push, email = email)
                .onFailure { _uiState.update { it.copy(children = previous, saveFailed = true) } }
        }
    }
}
