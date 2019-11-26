package com.waz.zclient.core.data.source.remote

import com.waz.zclient.user.data.source.remote.UserApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

open class Network {

    internal var retrofit: Retrofit

    private val apiToken = "5U-hv-hx4bjB62cFLmIbopzkXyTEo7TvKSospAqrj3ySv5E075-k7Lv-feB6_JmESlMa-dRqsk10AcyEcGNTBw==.v=1.k=1.d=1574776050.t=a.l=.u=4555f7b2-f97b-409f-8c3a-333a473ac1b9.c=5543355581247499544\n"

    init {
        retrofit = createNetworkClient(BASE_URL)
    }

    private fun createNetworkClient(baseUrl: String): Retrofit {
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(apiToken)).build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()
    }

    fun getUserApi(): UserApi {
        return retrofit.create(UserApi::class.java)
    }

    companion object {
        private const val BASE_URL = "https://staging-nginz-https.zinfra.io"
    }

}




