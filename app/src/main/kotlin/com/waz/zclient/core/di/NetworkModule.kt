package com.waz.zclient.core.di

import com.waz.zclient.BuildConfig
import com.waz.zclient.core.network.AccessTokenAuthenticator
import com.waz.zclient.core.network.AccessTokenInterceptor
import com.waz.zclient.core.network.AccessTokenRepository
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.AuthTokenHandler
import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.core.network.RetrofitClient
import com.waz.zclient.core.threading.ThreadHandler
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkDependencyProvider {

    private const val BASE_URL = "https://staging-nginz-https.zinfra.io"

    fun retrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    fun createClient(accessTokenInterceptor: AccessTokenInterceptor,
                     accessTokenAuthenticator: AccessTokenAuthenticator): OkHttpClient =
        OkHttpClient.Builder().apply {
            addInterceptor(accessTokenInterceptor)
            authenticator(accessTokenAuthenticator)
            if (BuildConfig.DEBUG) {
                val loggingInterceptor = HttpLoggingInterceptor().setLevel(Level.BASIC)
                addInterceptor(loggingInterceptor)
            }
        }.build()
}

val networkModule: Module = module {
    single { NetworkHandler(androidContext()) }
    single { NetworkDependencyProvider.createClient(get(), get()) }
    single { NetworkDependencyProvider.retrofit(get()) }
    single { AccessTokenRepository() }
    single { AuthTokenHandler(get()) }
    single { AccessTokenAuthenticator(get()) }
    single<NetworkClient> { RetrofitClient(get()) }
    single { ThreadHandler() }
    single { ApiService(get(), get(), get()) }
}
