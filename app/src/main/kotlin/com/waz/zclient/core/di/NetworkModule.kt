package com.waz.zclient.core.di

import com.waz.zclient.BuildConfig
import com.waz.zclient.core.di.NetworkDependencyProvider.API_SERVICE_FOR_TOKEN
import com.waz.zclient.core.di.NetworkDependencyProvider.NETWORK_CLIENT_FOR_TOKEN
import com.waz.zclient.core.di.NetworkDependencyProvider.createHttpClient
import com.waz.zclient.core.di.NetworkDependencyProvider.createHttpClientForToken
import com.waz.zclient.core.di.NetworkDependencyProvider.retrofit
import com.waz.zclient.core.network.*
import com.waz.zclient.core.threading.ThreadHandler
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkDependencyProvider {

    private const val BASE_URL = "https://staging-nginz-https.zinfra.io"

    const val API_SERVICE_FOR_TOKEN = "API_SERVICE_FOR_TOKEN"
    const val NETWORK_CLIENT_FOR_TOKEN = "NETWORK_CLIENT_FOR_TOKEN"

    fun retrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    fun createHttpClient(accessTokenInterceptor: AccessTokenInterceptor,
                         accessTokenAuthenticator: AccessTokenAuthenticator): OkHttpClient =
        OkHttpClient.Builder().apply {
            addInterceptor(accessTokenInterceptor)
            authenticator(accessTokenAuthenticator)
            if (BuildConfig.DEBUG) {
                addLoggingInterceptor()
            }
        }.build()

    fun createHttpClientForToken(): OkHttpClient =
        OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                addLoggingInterceptor()
            }
        }.build()

    private fun OkHttpClient.Builder.addLoggingInterceptor() {
        val loggingInterceptor = HttpLoggingInterceptor().setLevel(Level.BASIC)
        addInterceptor(loggingInterceptor)
    }
}

val networkModule: Module = module {
    single { ThreadHandler() }
    single { NetworkHandler(androidContext()) }
    single { createHttpClient(get(), get()) }
    single { retrofit(get()) }
    single { AccessTokenRemoteDataSource(get()) }
    single { AccessTokenRepository(get()) }
    single { AuthTokenHandler(get()) }
    single { AccessTokenAuthenticator(get()) }
    single<NetworkClient> { RetrofitClient(get()) }
    single { ApiService(get(), get(), get()) }

    //Token manipulation
    single<NetworkClient>(named(NETWORK_CLIENT_FOR_TOKEN)) { RetrofitClient(retrofit(createHttpClientForToken())) }
    single(named(API_SERVICE_FOR_TOKEN)) { ApiService(get(), get(), get(named(NETWORK_CLIENT_FOR_TOKEN))) }
    single { TokenService(get(named(API_SERVICE_FOR_TOKEN))) }
}

