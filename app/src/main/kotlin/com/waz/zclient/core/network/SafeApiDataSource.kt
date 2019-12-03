package com.waz.zclient.core.network

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

suspend fun <R> requestNetwork(networkRequest: suspend () -> Either<Failure, R>): Either<Failure, R> =
    try {
        networkRequest()
    } catch (e: Exception) {
        Either.Left(Failure(e.localizedMessage))
    }


// TODO: UNTESTED.
// TODO test and improve once room has been integrated into this E2E solution.
suspend fun <R> requestData(databaseRequest: suspend () -> Either<Failure, R>,
                            networkRequest: suspend () -> Either<Failure, R>,
                            saveCallRequest: suspend () -> Unit): Either<Failure, R> {
    var response = databaseRequest()
    if (response.isRight) {
        val networkResponse = networkRequest()
        if (networkResponse.isRight) {
            saveCallRequest()
        }
        response = networkResponse
    }
    return response
}
