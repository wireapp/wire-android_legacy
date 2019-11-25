package com.waz.zclient.settings.data.source.remote

import com.waz.zclient.R
import com.waz.zclient.settings.presentation.model.SettingsItem
import com.waz.zclient.utilities.config.ConfigHelper
import com.waz.zclient.utilities.resources.ResourceManager
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.protobuf.ProtoConverterFactory



class Network(){

    private var retrofit: Retrofit

    init {
        retrofit =  createNetworkClient(BASE_URL)
    }
    private fun createNetworkClient(baseUrl: String): Retrofit {

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()
    }

    fun getUserApi() : UserApi{
        return retrofit.create(UserApi::class.java)
    }

    companion object {
        private  const val BASE_URL = "https://staging-nginz-https.zinfra.io"
    }

}




