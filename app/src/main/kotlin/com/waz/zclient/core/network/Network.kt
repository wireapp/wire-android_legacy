package com.waz.zclient.core.network

import com.waz.zclient.BuildConfig
import com.waz.zclient.user.data.source.remote.AuthHeaderInterceptor
import com.waz.zclient.user.data.source.remote.UserApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory


object Network {

    private const val BASE_URL = "https://staging-nginz-https.zinfra.io"

    private val retrofit: Retrofit by lazy { createNetworkClient(BASE_URL) }

    private fun createNetworkClient(baseUrl: String): Retrofit {

        val okHttpClient = OkHttpClient().newBuilder()
            .addInterceptor { chain ->

                val newRequest = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(newRequest)
            }
            .addInterceptor(AuthHeaderInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()
    }

    fun userApi(): UserApi {
        return retrofit.create(UserApi::class.java)
    }
}




