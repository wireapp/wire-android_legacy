package com.waz.zclient.core.network

import com.waz.zclient.BuildConfig
import com.waz.zclient.user.data.source.remote.UsersApi
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
                    .addHeader("Content-Type", "application/json")
                    .addHeader(
                        "Authorization", "Bearer $API_TOKEN").build()
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
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()
    }

    fun userApi(): UsersApi {
        return retrofit.create(UsersApi::class.java)
    }

    companion object {
        private const val BASE_URL = "https://staging-nginz-https.zinfra.io"
        //Hardcoded just for testing
        private const val API_TOKEN = "tuztNr_lQSBzNTR_lNTadzrgzuRFrb3Sq_lg6ULUnWLXiVIwBZQPu-u-Zlhbg2ozPstaXSDSTv576y9hlqXrDg==.v=1.k=1.d=1575566691.t=a.l=.u=aa4e0112-bc8c-493e-8677-9fde2edf3567.c=1324425809936862021"
    }
}




