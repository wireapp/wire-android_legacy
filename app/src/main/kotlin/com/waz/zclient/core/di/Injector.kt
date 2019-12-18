package com.waz.zclient.core.di

import android.content.Context
import com.waz.zclient.BuildConfig
import com.waz.zclient.ContextProvider
import com.waz.zclient.core.network.AccessTokenAuthenticator
import com.waz.zclient.core.network.AccessTokenInterceptor
import com.waz.zclient.core.network.AccessTokenRepository
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.AuthToken
import com.waz.zclient.core.network.NetworkClient
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.core.network.RetrofitClient
import com.waz.zclient.core.network.api.token.TokenApi
import com.waz.zclient.core.network.api.token.TokenService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Temporary class for providing dependencies.
 * TODO: Remove once a DI Framework is used.
 */
object Injector {

    private const val BASE_URL = "https://staging-nginz-https.zinfra.io"
    private const val API_TOKEN = "-HFiw9hJvFhu2djSIt1WZ0fwQg3J0a0RUFCQ3OXzXKB8yrJn9m1xcPsGXoQHDZjHCIk0bbuQwOnYam3cFbItDw==.v=1.k=1.d=1575391626.t=a.l=.u=4555f7b2-f97b-409f-8c3a-333a473ac1b9.c=2878560529346928087"

    private val retrofit: Retrofit = createRetrofit()

    private fun createRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(createClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun createClient(): OkHttpClient {
        val okHttpClientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
        okHttpClientBuilder.addInterceptor(accessTokenInterceptor())
        okHttpClientBuilder.authenticator(accessTokenAuthenticator())
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
            okHttpClientBuilder.addInterceptor(loggingInterceptor)
        }
        return okHttpClientBuilder.build()
    }

    fun networkHandler() = NetworkHandler(context())

    fun context(): Context = ContextProvider.getApplicationContext()

    fun networkClient(): NetworkClient = RetrofitClient(retrofit)

    private fun tokenApi(): TokenApi = networkClient().create(TokenApi::class.java)

    fun apiService() = ApiService(networkHandler())

    fun tokenService() = TokenService(tokenApi(), apiService())

    private fun accessTokenRepository() = AccessTokenRepository()

    private fun accessTokenAuthenticator(): AccessTokenAuthenticator =
        AccessTokenAuthenticator(AuthToken(accessTokenRepository()))

    private fun accessTokenInterceptor(): AccessTokenInterceptor =
        AccessTokenInterceptor(AuthToken(accessTokenRepository()))
}
