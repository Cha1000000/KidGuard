package ru.homelab.kidguard.data.alerts

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import ru.homelab.kidguard.core.domain.model.AlertEmailSaved
import ru.homelab.kidguard.core.domain.model.AlertSettings
import ru.homelab.kidguard.core.domain.model.ChildAlertPrefs
import ru.homelab.kidguard.core.domain.model.ChildAlertRow
import ru.homelab.kidguard.core.domain.repository.AlertSettingsRepository
import ru.homelab.kidguard.data.network.AuthApi
import ru.homelab.kidguard.data.network.ChildNotificationsRequest
import ru.homelab.kidguard.data.network.ChildrenApi
import ru.homelab.kidguard.data.network.UpdateAlertEmailRequest
import ru.homelab.kidguard.data.network.toDomain
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отдельный DataStore, а не общий `kidguard_settings` или `kidguard_child_alerts`: хранит ровно
 * один факт — включена ли шторка по ребёнку, — и читает его исключительно [pushEnabled], который
 * обязан быть синхронным и не трогать сеть (его зовут из фоновой проверки здоровья на каждый тик).
 * Смешивать с `kidguard_child_alerts` ([ru.homelab.kidguard.data.alerts.ChildAlertStoreImpl]) нельзя:
 * тот хранит СНИМОК состояния устройства для детекта перехода «было ок -> сломалось», это же —
 * подписку родителя на канал, у них разный смысл и разный жизненный цикл записи.
 */
private val Context.alertPrefsDataStore by preferencesDataStore(name = "kidguard_alert_prefs")

/**
 * Реализация [AlertSettingsRepository] поверх `GET/PATCH /me` и `GET/PATCH /children/{id}/notifications`.
 *
 * Отдельного эндпоинта «настройки оповещений одним запросом» на сервере нет — экран собирает
 * состояние из профиля родителя (адрес) и списка детей (флаги по каждому), поэтому [load] бьёт по
 * сети дважды. Это ровно те же данные, что уже показывают экраны «Профиль» и «Дети», второй сетевой
 * поход не страшнее один раз в открытие экрана «Оповещения».
 */
@Singleton
class AlertSettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authApi: AuthApi,
    private val childrenApi: ChildrenApi
) : AlertSettingsRepository {

    override suspend fun load(): Result<AlertSettings> = try {
        val user = authApi.getMe().user
        val children = childrenApi.listChildren().children

        // Локальный кэш push-флага обновляем целиком по свежему ответу сервера: сервер — источник
        // истины, DataStore — только его быстрый снимок для pushEnabled(). Расхождение (например,
        // родитель поменял флаг на другом устройстве) само себя чинит при следующем открытии экрана.
        context.alertPrefsDataStore.edit { prefs ->
            children.forEach { child -> prefs[pushKey(child.id)] = child.notifyPush }
        }

        val rows = children.map { dto ->
            ChildAlertRow(
                child = dto.toDomain(),
                prefs = ChildAlertPrefs(childId = dto.id, push = dto.notifyPush, email = dto.notifyEmail)
            )
        }

        Result.success(
            AlertSettings(
                accountEmail = user.email,
                alertEmail = user.alertEmail,
                children = rows
            )
        )
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun setChildPrefs(childId: Int, push: Boolean?, email: Boolean?): Result<Unit> = try {
        childrenApi.updateNotifications(childId, ChildNotificationsRequest(notifyPush = push, notifyEmail = email))
        // В кэш пишем только push: это единственный канал, который вообще читают локально
        // (см. KDoc pushEnabled в контракте) — письмо проверяет сервер, ему кэш не нужен.
        if (push != null) {
            context.alertPrefsDataStore.edit { prefs -> prefs[pushKey(childId)] = push }
        }
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun setAlertEmail(email: String?): Result<AlertEmailSaved> = try {
        val response = authApi.updateMe(UpdateAlertEmailRequest(alertEmail = email))
        Result.success(AlertEmailSaved(response.user.alertEmail, response.verificationSent))
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun pushFlags(): Map<Int, Boolean> {
        // Сеть — первый источник, кэш — запасной. Кэш обновляется только когда родитель откроет
        // экран «Оповещения» или сам двинет тумблер, поэтому он легко отстаёт: флаг, включённый с
        // другого устройства, сюда не доедет. Устаревшее «выключено» при этом молча гасит
        // единственный сигнал о поломке контроля, так что лишний запрос раз в 15 минут — цена,
        // которую стоит платить.
        val fresh = runCatching { childrenApi.listChildren().children }.getOrNull()
        if (fresh != null) {
            context.alertPrefsDataStore.edit { prefs ->
                fresh.forEach { child -> prefs[pushKey(child.id)] = child.notifyPush }
            }
            return fresh.associate { it.id to it.notifyPush }
        }

        // Сети нет: отдаём последнее известное. Ребёнка, которого в кэше нет, здесь просто не
        // будет — вызывающий трактует отсутствие как «показывать».
        return context.alertPrefsDataStore.data.first().asMap()
            .mapNotNull { (key, value) ->
                val id = key.name.removePrefix(PUSH_KEY_PREFIX).toIntOrNull()
                if (key.name.startsWith(PUSH_KEY_PREFIX) && id != null && value is Boolean) {
                    id to value
                } else {
                    null
                }
            }
            .toMap()
    }

    private fun pushKey(childId: Int) = booleanPreferencesKey("$PUSH_KEY_PREFIX$childId")

    private companion object {
        const val PUSH_KEY_PREFIX = "push_"
    }
}
