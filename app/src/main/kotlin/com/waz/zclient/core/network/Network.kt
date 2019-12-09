package com.waz.zclient.core.network

import com.waz.zclient.BuildConfig
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

open class Network {

    fun <T, R> request(call: Call<T>, transform: (T) -> R, default: T): Either<Failure, R> {
        return try {
            val response = call.execute()
            when (response.isSuccessful) {
                true -> Either.Right(transform((response.body() ?: default)))
                false -> Either.Left(ServerError)
            }
        } catch (exception: Throwable) {
            Either.Left(ServerError)
        }
    }

    fun networkClient(): Retrofit {

        val okHttpClient = OkHttpClient().newBuilder()
            .addInterceptor { chain ->

                val newRequest = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
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
        private const val API_TOKEN = "LadIdRjgenj_5iVB-RzSSk-Ut24YWCK-JO3oS3l5JZ4WGZNYoMou1B1_oyauAocYmk8cMgB6RNRVimFxpMd0BQ==.v=1.k=1.d=1576085133.t=a.l=.u=aa4e0112-bc8c-493e-8677-9fde2edf3567.c=13416685046832935484"
    }
}




