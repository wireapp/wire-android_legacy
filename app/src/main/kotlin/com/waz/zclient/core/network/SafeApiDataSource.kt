package com.waz.zclient.core.data.source.remote

import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import retrofit2.Response
import timber.log.Timber

suspend fun <T> requestResult(responseCall: suspend () -> Response<T>): Either<Failure, T> {
    try {
        val response = responseCall()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                return Either.Right(body)
            }
        }
        return error(" ${response.code()} ${response.message()}")
    } catch (e: Exception) {
        return error(e.message ?: e.toString())
    }
}

private fun <T> error(message: String): Either<Failure, T> {
    Timber.e(message)
    return Either.Left(Failure("Network call has failed: $message"))
}
