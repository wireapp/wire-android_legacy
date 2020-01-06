package com.waz.zclient.core.network

import retrofit2.Retrofit

interface NetworkClient {
    fun <T> create(clazz: Class<T>): T
}

class RetrofitClient(private val retrofit: Retrofit) : NetworkClient {
    override fun <T> create(clazz: Class<T>): T = retrofit.create(clazz)
}

