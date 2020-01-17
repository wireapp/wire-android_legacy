package com.waz.zclient.core.di

import com.waz.zclient.BuildConfig
import com.waz.zclient.core.di.NetworkDependencyProvider.createHttpClient
import com.waz.zclient.core.di.NetworkDependencyProvider.createHttpClientForToken
import com.waz.zclient.core.di.NetworkDependencyProvider.retrofit
import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.core.network.RetrofitClient
import com.waz.zclient.core.network.accesstoken.AccessTokenAuthenticator
import com.waz.zclient.core.network.accesstoken.AccessTokenInterceptor
import com.waz.zclient.core.network.accesstoken.AccessTokenLocalDataSource
import com.waz.zclient.core.network.accesstoken.AccessTokenMapper
import com.waz.zclient.core.network.accesstoken.AccessTokenRemoteDataSource
import com.waz.zclient.core.network.accesstoken.AccessTokenRepository
import com.waz.zclient.core.network.accesstoken.RefreshTokenMapper
import com.waz.zclient.core.network.api.token.TokenApi
import com.waz.zclient.core.network.api.token.TokenService
import com.waz.zclient.storage.db.GlobalDatabase
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
    single { NetworkHandler(androidContext()) }
    single { createHttpClient(get(), get()) }
    single { retrofit(get()) }
    single { AccessTokenRemoteDataSource(get()) }
    single { AccessTokenLocalDataSource(get(), get<GlobalDatabase>().activeAccountsDao()) }
    single { AccessTokenMapper() }
    single { RefreshTokenMapper() }
    single { AccessTokenRepository(get(), get(), get(), get()) }
    single { AccessTokenAuthenticator(get(), get()) }
    single { AccessTokenInterceptor(get()) }
    single<NetworkClient> { RetrofitClient(get()) }

    //Token manipulation
    val networkClientForToken = "NETWORK_CLIENT_FOR_TOKEN"
    single<NetworkClient>(named(networkClientForToken)) { RetrofitClient(retrofit(createHttpClientForToken())) }
    single { get<NetworkClient>(named(networkClientForToken)).create(TokenApi::class.java) }
    single { TokenService(get(), get()) }
}
