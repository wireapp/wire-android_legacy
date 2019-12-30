package com.waz.zclient.core.di

import android.content.Context
import com.waz.zclient.BuildConfig
import com.waz.zclient.core.di.NetworkDependencyProvider.createHttpClient
import com.waz.zclient.core.di.NetworkDependencyProvider.createHttpClientForToken
import com.waz.zclient.core.di.NetworkDependencyProvider.retrofit
import com.waz.zclient.core.network.*
import com.waz.zclient.core.network.api.token.TokenApi
import com.waz.zclient.core.network.api.token.TokenService
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

    fun retrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    fun createHttpClient(accessTokenInterceptor: AccessTokenInterceptor,
                         accessTokenAuthenticator: AccessTokenAuthenticator): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(accessTokenInterceptor)
            .authenticator(accessTokenAuthenticator)
            .addLoggingInterceptor()
            .build()

    fun createHttpClientForToken(): OkHttpClient =
        OkHttpClient.Builder().addLoggingInterceptor().build()

    private fun OkHttpClient.Builder.addLoggingInterceptor() = this.apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(HttpLoggingInterceptor().setLevel(Level.BODY))
        }
    }
}

val networkModule: Module = module {
    single { ThreadHandler() }
    single { NetworkHandler(androidContext()) }
    single { createHttpClient(get(), get()) }
    single { retrofit(get()) }
    single { AccessTokenRemoteDataSource(get()) }
    factory {
        AccessTokenLocalDataSource(
            //TODO: get from other(global?) module
            androidContext().getSharedPreferences("DUMMY_USER_PREFS", Context.MODE_PRIVATE)
        )
    }
    single { AccessTokenRepository(get(), get()) }
    single { AuthTokenHandler(get()) }
    single { AccessTokenAuthenticator(get()) }
    single { AccessTokenInterceptor(get()) }
    single<NetworkClient> { RetrofitClient(get()) }
    single { ApiService(get(), get(), get()) }

    //Token manipulation
    val apiServiceForToken = "API_SERVICE_FOR_TOKEN"
    val networkClientForToken = "NETWORK_CLIENT_FOR_TOKEN"
    single<NetworkClient>(named(networkClientForToken)) { RetrofitClient(retrofit(createHttpClientForToken())) }
    single(named(apiServiceForToken)) { ApiService(get(), get(), get(named(networkClientForToken))) }
    factory { get<ApiService>(named(apiServiceForToken)).createApi(TokenApi::class.java) }
    single { TokenService(get(named(apiServiceForToken)), get()) }
}

