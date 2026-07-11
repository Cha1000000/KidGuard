package ru.homelab.kidguard.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import ru.homelab.kidguard.data.auth.AuthLocalStore
import ru.homelab.kidguard.data.di.NetworkModule
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket-клиент push-канала сервера (веха 4.6). Сервер шлёт `{type:"policy-changed", childId}`
 * при каждом сохранении политики — подписчик в ответ делает немедленный pull.
 */
@Singleton
class PolicySocket @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val authLocalStore: AuthLocalStore
) {

    /**
     * Поток childId из событий policy-changed. «Вечный»: сам переподключается с бэкоффом
     * (5с → 60с) и живёт, пока жив собирающий scope (петли синхронизации). Периодический pull
     * остаётся страховкой на случай долгого разрыва.
     */
    fun events(): Flow<Int> = callbackFlow {
        val connectionLoop = launch {
            var backoffMs = INITIAL_BACKOFF_MS
            while (isActive) {
                val token = authLocalStore.currentBearerToken()
                if (token == null) {
                    // Сессии ещё нет (не вошли/не привязались) — подождать и проверить снова.
                    delay(MAX_BACKOFF_MS)
                    continue
                }

                val closed = CompletableDeferred<Unit>()
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        backoffMs = INITIAL_BACKOFF_MS
                        Timber.tag(TAG).d("WS подключён")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        parsePolicyChanged(text)?.let { trySend(it) }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Timber.tag(TAG).d("WS разрыв: %s", t.message)
                        closed.complete(Unit)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Timber.tag(TAG).d("WS закрыт: %d %s", code, reason)
                        closed.complete(Unit)
                    }
                }

                val request = Request.Builder().url("$WS_URL?token=$token").build()
                val socket = okHttpClient.newWebSocket(request, listener)
                try {
                    closed.await()
                } finally {
                    socket.cancel()
                }

                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        awaitClose { connectionLoop.cancel() }
    }

    /** `{type:"policy-changed", childId}` → childId; всё остальное игнорируем. */
    private fun parsePolicyChanged(text: String): Int? = runCatching {
        val obj = Json.parseToJsonElement(text).jsonObject
        if (obj["type"]?.jsonPrimitive?.content == "policy-changed") {
            obj["childId"]?.jsonPrimitive?.int
        } else {
            null
        }
    }.getOrNull()

    private companion object {
        const val TAG = "KidGuardSync"
        const val INITIAL_BACKOFF_MS = 5_000L
        const val MAX_BACKOFF_MS = 60_000L

        /** Push-канал на том же хосте, что HTTP API (см. NetworkModule.BASE_URL). */
        val WS_URL = NetworkModule.BASE_URL.replaceFirst("http", "ws") + "ws"
    }
}
