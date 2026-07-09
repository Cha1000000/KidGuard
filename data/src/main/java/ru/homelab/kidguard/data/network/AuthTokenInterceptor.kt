package ru.homelab.kidguard.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import ru.homelab.kidguard.data.auth.AuthLocalStore
import javax.inject.Inject

/**
 * Подставляет `Authorization: Bearer <token>` в исходящие запросы, если сессия есть
 * (родительский JWT или детский device-токен — см. [AuthLocalStore.currentBearerToken]).
 * Запросам без сессии (напр. `POST /auth/google`, `POST /device/pair` до привязки) заголовок не
 * добавляется — токена ещё нет.
 *
 * `runBlocking` допустим: интерсептор выполняется на фоновом потоке OkHttp (не на main), а
 * DataStore после первого чтения отдаёт значение из памяти.
 */
class AuthTokenInterceptor @Inject constructor(
    private val authLocalStore: AuthLocalStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { authLocalStore.currentBearerToken() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
