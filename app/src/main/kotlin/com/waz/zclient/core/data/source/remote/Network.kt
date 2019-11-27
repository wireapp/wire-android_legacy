package com.waz.zclient.core.data.source.remote

import com.waz.zclient.user.data.source.remote.UserApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

open class Network {

    internal var retrofit: Retrofit

    init {
        retrofit = createNetworkClient(BASE_URL)
    }

    private fun createNetworkClient(baseUrl: String): Retrofit {

        val okHttpClient = OkHttpClient().newBuilder()
            .addInterceptor { chain ->
                val newRequest = chain.request().newBuilder()
                    .addHeader(
                        "Authorization", "Bearer $API_TOKEN").build()
                chain.proceed(newRequest)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()
    }

    fun getUserApi(): UserApi {
        return retrofit.create(UserApi::class.java)
    }

    companion object {
        private const val BASE_URL = "https://staging-nginz-https.zinfra.io"
        const val API_TOKEN = "iQ9keNRmijBNCQ67rPXXkaV7-vmg3RDTa98kg53daA7PHQKtizU_27vZlQgit6DIfOPVxs1N4t_aXLpYdEZxDA==.v=1.k=1.d=1574864332.t=a.l=.u=4555f7b2-f97b-409f-8c3a-333a473ac1b9.c=1887283590397930815"
    }

}




