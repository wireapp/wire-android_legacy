package com.waz.zclient.user.data.source.remote

import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory


class Network {

    private var retrofit: Retrofit

    init {
        retrofit = createNetworkClient(BASE_URL)
    }

    private fun createNetworkClient(baseUrl: String): Retrofit {

        return Retrofit.Builder()
            .baseUrl(baseUrl)
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




