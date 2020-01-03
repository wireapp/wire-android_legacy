package com.waz.zclient.core.network

import com.waz.zclient.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

open class Network {

    fun networkClient(): Retrofit {

        val okHttpClient = OkHttpClient().newBuilder()
            .addInterceptor { chain ->

                val newRequest = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("Authorization", "Bearer $API_TOKEN")
                    .build()
                chain.proceed(newRequest)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    companion object {
        private const val BASE_URL = "https://staging-nginz-https.zinfra.io"
        //Hardcoded just for testing
        private const val API_TOKEN = "vBImQ6xBQBjs-RgDTsr5m3VSTt_dcx7M2SxqOVHoEhd-_cizyQMZOJZihfr04ztugn9U6LqOD_8uZoNTsbk5Aw==.v=1.k=1.d=1578049398.t=a.l=.u=aa4e0112-bc8c-493e-8677-9fde2edf3567.c=2968515869958284437"
    }
}




