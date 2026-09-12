package com.viple.looby.core.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.viple.looby.BuildConfig
import com.viple.looby.core.network.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    @Named("baseUrl")
    fun baseUrl(): String = BuildConfig.API_BASE_URL.trimEnd('/') + "/"

    private fun baseClient(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("Accept-Language", java.util.Locale.getDefault().toLanguageTag())
                    .header("User-Agent", "Looby-Android/${BuildConfig.VERSION_NAME}")
                    .build()
            )
        }
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }

    /** Client sans auth : images, upload Blob, refresh. */
    @Provides
    @Singleton
    @Named("plain")
    fun plainClient(): OkHttpClient = baseClient().build()

    @Provides
    @Singleton
    @Named("authed")
    fun authedClient(authInterceptor: AuthInterceptor, authenticator: TokenAuthenticator): OkHttpClient =
        baseClient().addInterceptor(authInterceptor).authenticator(authenticator).build()

    @Provides
    @Singleton
    fun retrofit(@Named("authed") client: OkHttpClient, json: Json, @Named("baseUrl") baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton fun mobileApi(retrofit: Retrofit): MobileApi = retrofit.create(MobileApi::class.java)
    @Provides @Singleton fun catalogApi(retrofit: Retrofit): CatalogApi = retrofit.create(CatalogApi::class.java)
    @Provides @Singleton fun messagingApi(retrofit: Retrofit): MessagingApi = retrofit.create(MessagingApi::class.java)
    @Provides @Singleton fun accountApi(retrofit: Retrofit): AccountApi = retrofit.create(AccountApi::class.java)
    @Provides @Singleton fun supportApi(retrofit: Retrofit): SupportApi = retrofit.create(SupportApi::class.java)
    @Provides @Singleton fun livriaApi(retrofit: Retrofit): LivriaApi = retrofit.create(LivriaApi::class.java)
}
