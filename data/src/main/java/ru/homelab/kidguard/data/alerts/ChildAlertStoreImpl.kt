package ru.homelab.kidguard.data.alerts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.domain.model.DeviceHealth
import ru.homelab.kidguard.core.domain.repository.ChildAlertStore
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.childAlertStore by preferencesDataStore(name = "kidguard_child_alerts")

/**
 * Хранит снимок состояния детей в DataStore. Пишем ровно то, от чего зависит решение уведомлять:
 * время последнего выхода на связь и флаги здоровья. Аватар и прочее для сравнения не нужны.
 */
@Singleton
class ChildAlertStoreImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ChildAlertStore {

    @Serializable
    private data class StoredChild(
        val id: Int,
        val name: String,
        val paired: Boolean,
        val lastSeenAtEpochSeconds: Long? = null,
        val hasHealth: Boolean = false,
        val accessibility: Boolean = true,
        val overlay: Boolean = true,
        val deviceAdmin: Boolean = true,
        val vpn: Boolean = true,
        val batteryOptimization: Boolean = true
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(StoredChild.serializer())

    override suspend fun previous(): Map<Int, Child> {
        val raw = context.childAlertStore.data.first()[KEY] ?: return emptyMap()
        val stored: List<StoredChild> = runCatching { json.decodeFromString(serializer, raw) }
            .getOrElse { emptyList() }
        return stored.associate { it.id to it.toChild() }
    }

    override suspend fun save(children: List<Child>) {
        val raw = json.encodeToString(serializer, children.map { it.toStored() })
        context.childAlertStore.edit { it[KEY] = raw }
    }

    override suspend fun clear() {
        context.childAlertStore.edit { it.clear() }
    }

    private fun Child.toStored() = StoredChild(
        id = id,
        name = name,
        paired = paired,
        lastSeenAtEpochSeconds = lastSeenAt?.epochSecond,
        hasHealth = health != null,
        accessibility = health?.accessibility ?: true,
        overlay = health?.overlay ?: true,
        deviceAdmin = health?.deviceAdmin ?: true,
        vpn = health?.vpn ?: true,
        batteryOptimization = health?.batteryOptimization ?: true
    )

    private fun StoredChild.toChild() = Child(
        id = id,
        name = name,
        avatar = 0,
        paired = paired,
        lastSeenAt = lastSeenAtEpochSeconds?.let(Instant::ofEpochSecond),
        health = if (hasHealth) {
            DeviceHealth(accessibility, overlay, deviceAdmin, vpn, batteryOptimization)
        } else {
            null
        }
    )

    private companion object {
        val KEY = stringPreferencesKey("last_known_children")
    }
}
