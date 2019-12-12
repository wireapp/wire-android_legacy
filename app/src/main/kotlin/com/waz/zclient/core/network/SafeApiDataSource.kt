package com.waz.zclient.core.network

import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Failure
import com.waz.zclient.core.functional.map
import retrofit2.Response
import timber.log.Timber
import java.util.concurrent.CancellationException

suspend fun <T> requestApi(responseCall: suspend () -> Response<T>): Either<Failure, T> {
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

suspend fun <R> requestData(networkRequest: suspend () -> Either<Failure, R>): Either<Failure, R> =
    try {
        networkRequest()
    } catch (e: CancellationException) {
        Either.Left(Failure(e.localizedMessage))
    }

suspend fun <R> requestLocal(localRequest: suspend () -> R): Either<Failure, R> =
    try {
        Either.Right(localRequest())
    } catch (e: CancellationException) {
        Either.Left(Failure(e.localizedMessage))
    }

suspend fun <R> resultEither(databaseRequest: suspend () -> Either<Failure, R>,
                             networkRequest: suspend () -> Either<Failure, R>,
                             saveCallRequest: (R) -> Unit): Either<Failure, R> {
    var response = databaseRequest()
    if (response.isLeft) {
        val networkResponse = requestData { networkRequest() }
        if (networkResponse.isRight) {
            networkResponse.map(saveCallRequest)
        }
        response = networkResponse
    }
    return response
}
