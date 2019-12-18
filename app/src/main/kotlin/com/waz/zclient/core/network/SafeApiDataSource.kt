package com.waz.zclient.core.network

import com.waz.zclient.core.exception.*
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import kotlinx.coroutines.runBlocking
import retrofit2.Response
import timber.log.Timber

suspend fun <T> requestApi(responseCall: suspend () -> Response<T>): Either<NetworkFailure, T> {
    try {
        val response = responseCall()
        if (response.isSuccessful) {
            val body = response.body()
            body?.let {
                return Either.Right(body)
            }
        }
        return Either.Left(HttpError(response.code(), response.message()))
    } catch (e: Exception) {
        return Either.Left(NetworkServiceError)
    }
}

suspend fun <R> requestDatabase(localRequest: suspend () -> R): Either<DatabaseFailure, R> =
    try {
        Either.Right(localRequest())
    } catch (e: Exception) {
        Either.Left(DatabaseError)
    }


suspend fun <R> accessData(mainRequest: suspend () -> Either<Failure, R>,
                           fallbackRequest: suspend () -> Either<Failure, R>,
                           saveToDatabase: suspend (R) -> Unit): Either<Failure, R> =

    with(mainRequest()) {
        onFailure {
            when (it) {
                is DatabaseError -> performFallback(fallbackRequest, saveToDatabase)
                else -> Timber.e("Database request failed with unknown error ")
            }
        }
    }

private fun <R> performFallback(fallbackRequest: suspend () -> Either<Failure, R>,
                                saveToDatabase: suspend (R) -> Unit): Either<Failure, R> =
    runBlocking {
        with(fallbackRequest()) {
            onSuccess { runBlocking { saveToDatabase(it) } }
            onFailure {
                when (it) {
                    is NetworkServiceError -> Timber.e("Network request failed with generic error ")
                    is HttpError -> Timber.e("Network request failed with {${it.errorCode} ${it.errorMessage} ")
                    else -> Timber.e("Network request failed with unknown error ")
                }
            }
        }
    }
