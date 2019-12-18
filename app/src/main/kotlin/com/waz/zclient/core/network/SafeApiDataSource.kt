package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import kotlinx.coroutines.runBlocking
import retrofit2.Response
import timber.log.Timber

suspend fun <T> requestApi(responseCall: suspend () -> Response<T>): Either<Failure, T> {
    try {
        val response = responseCall()
        if (response.isSuccessful) {
            val body = response.body()
            body?.let {
                return Either.Right(body)
            }
        }
        return Either.Left(Failure.HttpError(response.code(), response.message()))
    } catch (e: Exception) {
        return Either.Left(Failure.NetworkServiceError)
    }
}

suspend fun <R> requestDatabase(localRequest: suspend () -> R): Either<Failure, R> =
    try {
        Either.Right(localRequest())
    } catch (e: Exception) {
        Either.Left(Failure.DatabaseError)
    }


suspend fun <R> accessData(mainRequest: suspend () -> Either<Failure, R>,
                           fallbackRequest: suspend () -> Either<Failure, R>,
                           saveToDatabase: suspend (R) -> Unit): Either<Failure, R> =

    with(mainRequest()) {
        onFailure { mainFailure ->
            when (mainFailure) {
                is Failure.DatabaseError ->
                    performFallback(fallbackRequest, saveToDatabase)
                else ->
                    Timber.e("Database request failed with unknown error ")
            }
        }
    }

private fun <R> performFallback(fallbackRequest: suspend () -> Either<Failure, R>,
                                saveToDatabase: suspend (R) -> Unit): Either<Failure, R> =
    runBlocking {
        with(fallbackRequest()) {
            onSuccess { runBlocking { saveToDatabase(it) } }
            onFailure { fallbackFailure ->
                when (fallbackFailure) {
                    is Failure.NetworkServiceError ->
                        Timber.e("Network request failed with generic error ")
                    is Failure.HttpError ->
                        Timber.e("Network request failed with {${fallbackFailure.errorCode} ${fallbackFailure.errorMessage} ")
                    else ->
                        Timber.e("Network request failed with unknown error ")
                }
            }
        }
    }
