package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import kotlinx.coroutines.CancellationException
import retrofit2.Response

suspend fun <T> requestApi(responseCall: suspend () -> Response<T>): Either<Failure, T> {
    try {
        val response = responseCall()
        if (response.isSuccessful) {
            val body = response.body()
            body?.let {
                return Either.Right(body)
            }
        }
        return Either.Left(Failure.ServerError(response.code(), response.message()))
    } catch (e: CancellationException) {
        return Either.Left(Failure.CancellationError)
    }
}

suspend fun <R> requestData(request: suspend () -> Either<Failure, R>): Either<Failure, R> =
    try {
        request()
    } catch (e: CancellationException) {
        Either.Left(Failure.CancellationError)
    }

suspend fun <R> requestLocal(localRequest: suspend () -> R): Either<Failure, R> =
    try {
        Either.Right(localRequest())
    } catch (e: CancellationException) {
        Either.Left(Failure.CancellationError)
    }

suspend fun <R> resultEither(mainRequest: suspend () -> Either<Failure, R>,
                             fallbackRequest: suspend () -> Either<Failure, R>,
                             saveToDatabase: (R) -> Unit): Either<Failure, R> {
    var response = mainRequest()
    if (response.isLeft) {
        val networkResponse = requestData { fallbackRequest() }
        if (networkResponse.isRight) {
            networkResponse.map(saveToDatabase)
        }
        response = networkResponse
    }
    return response
}
