package com.waz.zclient.core.di

import android.content.Context
import com.waz.zclient.BuildConfig
import com.waz.zclient.ContextProvider
import com.waz.zclient.ZApplication
import com.waz.zclient.core.network.*
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

    fun retrofit(): Retrofit {
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
            val loggingInterceptor = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)
            okHttpClientBuilder.addInterceptor(loggingInterceptor)
        }
        return okHttpClientBuilder.build()
    }

    fun networkHandler() = NetworkHandler(context())

    fun context(): Context = ContextProvider.getApplicationContext()

    private fun accessTokenAuthenticator(): AccessTokenAuthenticator =
        AccessTokenAuthenticator(AuthToken(AccessTokenRepository()))

    private fun accessTokenInterceptor(): AccessTokenInterceptor =
        AccessTokenInterceptor(AuthToken(AccessTokenRepository()))
}
