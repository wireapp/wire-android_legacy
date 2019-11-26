package com.waz.zclient.core.data.source.remote

import retrofit2.Response
import timber.log.Timber

abstract class BaseRemoteDataSource {

    protected suspend fun <T> getResult(call: suspend () -> Response<T>): RequestResult<T> {
        try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) return RequestResult.success(body)
            }
            return error(" ${response.code()} ${response.message()}")
        } catch (e: Exception) {
            return error(e.message ?: e.toString())
        }
    }

    private fun <T> error(message: String): RequestResult<T> {
        Timber.e(message)
        return RequestResult.error("Network call has failed: $message")
    }

}
