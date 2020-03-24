@file:Suppress("MatchingDeclarationName")

package com.waz.zclient.core.network.di

import com.waz.zclient.BuildConfig
import com.waz.zclient.core.backend.Backend
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
import com.waz.zclient.core.network.connection.ConnectionSpecsFactory
import com.waz.zclient.core.network.di.NetworkDependencyProvider.createHttpClient
import com.waz.zclient.core.network.di.NetworkDependencyProvider.createHttpClientForToken
import com.waz.zclient.core.network.di.NetworkDependencyProvider.retrofit
import com.waz.zclient.core.network.proxy.HttpProxyFactory
import com.waz.zclient.core.network.useragent.UserAgentInterceptor
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

    fun retrofit(
        okHttpClient: OkHttpClient,
        backend: Backend
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    fun createHttpClient(
        accessTokenInterceptor: AccessTokenInterceptor,
        accessTokenAuthenticator: AccessTokenAuthenticator,
        userAgentInterceptor: UserAgentInterceptor,
        backend: Backend
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectionSpecs(ConnectionSpecsFactory.createConnectionSpecs())
            .addInterceptor(accessTokenInterceptor)
            .addInterceptor(userAgentInterceptor)
            .authenticator(accessTokenAuthenticator)
            .addLoggingInterceptor()
            .build()

    fun createHttpClientForToken(
        userAgentInterceptor: UserAgentInterceptor,
        backend: Backend
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectionSpecs(ConnectionSpecsFactory.createConnectionSpecs())
            .addInterceptor(userAgentInterceptor)
            .addLoggingInterceptor()
            .build()

    private fun OkHttpClient.Builder.addLoggingInterceptor() = this.apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(HttpLoggingInterceptor().setLevel(Level.BODY))
        }
    }
}

val networkModule: Module = module {
    single { NetworkHandler(androidContext()) }
    single { createHttpClient(get(), get(), get(), get()) }
    single { retrofit(get(), get()) }
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
    single<NetworkClient>(named(networkClientForToken)) { RetrofitClient(retrofit(createHttpClientForToken(get(), get()), get())) }
    single { get<NetworkClient>(named(networkClientForToken)).create(TokenApi::class.java) }
    single { TokenService(get(), get()) }
}
