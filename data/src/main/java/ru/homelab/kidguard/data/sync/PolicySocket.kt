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
import kotlinx.coroutines.withTimeoutOrNull
import ru.homelab.kidguard.data.auth.AuthLocalStore
import ru.homelab.kidguard.data.network.ServerConfig
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Событие push-канала сервера. */
sealed interface WsEvent {
    /** Политика ребёнка изменена — подписчик делает немедленный pull. */
    data class PolicyChanged(val childId: Int) : WsEvent

    /** WebSocket переподключился после разрыва — нужно подтянуть пропущенные изменения. */
    data object Reconnected : WsEvent

    /** Детское устройство ввело pairing-код — у ребёнка сменился статус привязки. */
    data class ChildPaired(val childId: Int) : WsEvent

    /** Отчёт детского устройства о здоровье изменился — родителю пора перепроверить контроль. */
    data class ChildHealthChanged(val childId: Int) : WsEvent
}

/**
 * WebSocket-клиент push-канала сервера (веха 4.6). Сервер шлёт `{type:"policy-changed", childId}`
 * при каждом сохранении политики и `{type:"child-paired", childId}` при привязке устройства.
 */
@Singleton
class PolicySocket @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val authLocalStore: AuthLocalStore
) {

    /**
     * Отдельный клиент для WebSocket — тот же пул соединений и потоков, но со своим ping-интервалом.
     * OkHttp сам шлёт ping-кадры и роняет соединение, если pong не пришёл вовремя: без этого
     * полуоткрытый сокет (сеть отвалилась молча, без RST) выглядит живым сколь угодно долго.
     * На общий HTTP-клиент интервал не ставим — там он не нужен и влиял бы на все запросы.
     */
    private val wsClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Поток событий push-канала. «Вечный»: сам переподключается с бэкоффом (5с → 60с) и живёт,
     * пока жив собирающий scope (петли синхронизации).
     *
     * Три уровня защиты от потери событий, от дешёвого к дорогому:
     * 1. ping/pong ([PING_INTERVAL_SECONDS]) роняет полуоткрытый сокет за десятки секунд;
     * 2. сторож канала ([SESSION_MAX_MS]) пересоздаёт даже исправное на вид соединение — против
     *    случая, когда сокет жив, а события до приложения не доходят;
     * 3. периодический pull петли синхронизации — последняя страховка на случай долгого разрыва.
     *
     * Каждое переподключение отдаёт [WsEvent.Reconnected]: пока связи не было, сервер мог послать
     * `policy-changed`, и повторно он его не пришлёт — подписчик обязан сам сходить за политикой.
     */
    fun events(): Flow<WsEvent> = callbackFlow {
        val connectionLoop = launch {
            var backoffMs = INITIAL_BACKOFF_MS
            // Был ли уже успешный onOpen: отличает первое подключение от переподключения после
            // разрыва. Atomic, а не обычный var, потому что пишется из потока OkHttp (колбэки
            // листенера), а читается и сбрасывается здесь, в корутине цикла.
            val wasConnected = AtomicBoolean(false)
            while (isActive) {
                val token = authLocalStore.currentBearerToken()
                if (token == null) {
                    // Сессии ещё нет (не вошли/не привязались) — подождать и проверить снова.
                    delay(MAX_BACKOFF_MS)
                    wasConnected.set(false)
                    continue
                }

                val closed = CompletableDeferred<Unit>()
                // Признак планового пересоздания: `cancel()` ниже всё равно дёрнет onFailure, и без
                // этого флага здоровая ротация писала бы в лог «WS разрыв», путая при разборе.
                val rotating = AtomicBoolean(false)
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        backoffMs = INITIAL_BACKOFF_MS
                        // getAndSet: первое подключение молчит (pull на старте петли и так есть),
                        // а каждое последующее означает, что канал рвался — за время разрыва
                        // сервер мог послать policy-changed, и повторно он его не пришлёт.
                        if (wasConnected.getAndSet(true)) {
                            trySend(WsEvent.Reconnected)
                        }
                        Timber.tag(TAG).d("WS подключён")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        parseEvent(text)?.let { trySend(it) }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (!rotating.get()) Timber.tag(TAG).d("WS разрыв: %s", t.message)
                        closed.complete(Unit)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (!rotating.get()) Timber.tag(TAG).d("WS закрыт: %d %s", code, reason)
                        closed.complete(Unit)
                    }
                }

                val request = Request.Builder().url("${ServerConfig.WS_URL}?token=$token").build()
                val socket = wsClient.newWebSocket(request, listener)
                // Сторож канала. Сокет может остаться живым — сервер отвечает на ping, разрыва нет —
                // и при этом перестать доставлять события: так на эмуляторе три подряд
                // `policy-changed` ушли в никуда, пока то же событие на отдельном соединении с тем
                // же токеном приходило штатно. Изнутри клиента такое не отличить от «просто тихо»,
                // поэтому раз в SESSION_MAX_MS соединение пересоздаётся принудительно: новый connect
                // заново регистрирует нас на сервере, а onOpen отдаёт [WsEvent.Reconnected] → pull.
                // Это же и потолок устаревания политики, если push молчит.
                val rotated = try {
                    withTimeoutOrNull(SESSION_MAX_MS) { closed.await() } == null
                } finally {
                    rotating.set(true)
                    socket.cancel()
                }

                if (rotated) {
                    Timber.tag(TAG).d("Плановое пересоздание WS (сторож канала)")
                    // Бэкофф не нужен: соединение было исправно, ждать перед новым нечего.
                    continue
                }

                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        awaitClose { connectionLoop.cancel() }
    }

    /** Разбирает `{type, childId}` в [WsEvent]; неизвестные типы игнорируем. */
    private fun parseEvent(text: String): WsEvent? = runCatching {
        val obj = Json.parseToJsonElement(text).jsonObject
        val childId = obj["childId"]?.jsonPrimitive?.int ?: return null
        when (obj["type"]?.jsonPrimitive?.content) {
            "policy-changed" -> WsEvent.PolicyChanged(childId)
            "child-paired" -> WsEvent.ChildPaired(childId)
            "child-health-changed" -> WsEvent.ChildHealthChanged(childId)
            else -> null
        }
    }.getOrNull()

    private companion object {
        const val TAG = "KidGuardSync"
        const val INITIAL_BACKOFF_MS = 5_000L
        const val MAX_BACKOFF_MS = 60_000L

        /** Как часто OkHttp шлёт ping и ждёт pong; без pong соединение падает в onFailure. */
        const val PING_INTERVAL_SECONDS = 30L

        /**
         * Срок жизни одного WS-соединения. Он же — потолок устаревания политики на устройстве,
         * когда push молчит: каждое пересоздание тянет за собой pull. Пятнадцатиминутный
         * [SyncRepositoryImpl.CHILD_PULL_INTERVAL_MS] при этом не трогаем — на нём висят ещё
         * и отправка статистики, приложений и heartbeat, а их учащать незачем.
         */
        const val SESSION_MAX_MS = 5L * 60 * 1000
    }
}
