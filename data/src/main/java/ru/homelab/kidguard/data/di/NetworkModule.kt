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
import ru.homelab.kidguard.data.network.AppsApi
import ru.homelab.kidguard.data.network.AuthApi
import ru.homelab.kidguard.data.network.AuthTokenInterceptor
import ru.homelab.kidguard.data.network.ChildrenApi
import ru.homelab.kidguard.data.network.PolicyApi
import ru.homelab.kidguard.data.network.ServerConfig
import ru.homelab.kidguard.data.network.UsageApi
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Адрес сервера — в ServerConfig (:data/network): зависит от типа сборки.

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
            .baseUrl(ServerConfig.BASE_URL)
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

    @Provides
    @Singleton
    fun provideAppsApi(retrofit: Retrofit): AppsApi = retrofit.create(AppsApi::class.java)
}
