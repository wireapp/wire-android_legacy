package com.waz.zclient.core.data.source.remote

import com.waz.zclient.core.resources.Resource
import retrofit2.Response
import timber.log.Timber

abstract class SafeApiDataSource {

    protected suspend fun <T> getRequestResult(responseCall: suspend () -> Response<T>): Resource<T> {
        try {
            val response = responseCall()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    return Resource.success(body)
                }
            }
            return error(" ${response.code()} ${response.message()}")
        } catch (e: Exception) {
            return error(e.message ?: e.toString())
        }
    }

    private fun <T> error(message: String): Resource<T> {
        Timber.e(message)
        return Resource.error("Network call has failed: $message")
    }

}
