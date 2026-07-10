package ru.homelab.kidguard.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.homelab.kidguard.data.network.AuthApi
import ru.homelab.kidguard.data.network.AuthTokenInterceptor
import ru.homelab.kidguard.data.network.ChildrenApi
import ru.homelab.kidguard.data.network.PolicyApi
import ru.homelab.kidguard.data.network.UsageApi
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // 10.0.2.2 — алиас эмулятора Android на localhost хост-машины: локальный dev-сервер
    // KidGuard-server (см. ~/projects/KidGuard-server), ещё не задеплоенный на боевой AdminVPS.
    // TODO(шаг 4.6): заменить на адрес поддомена после деплоя.
    private const val BASE_URL = "http://10.0.2.2:3003/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(authTokenInterceptor: AuthTokenInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authTokenInterceptor)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideChildrenApi(retrofit: Retrofit): ChildrenApi = retrofit.create(ChildrenApi::class.java)

    @Provides
    @Singleton
    fun providePolicyApi(retrofit: Retrofit): PolicyApi = retrofit.create(PolicyApi::class.java)

    @Provides
    @Singleton
    fun provideUsageApi(retrofit: Retrofit): UsageApi = retrofit.create(UsageApi::class.java)
}
