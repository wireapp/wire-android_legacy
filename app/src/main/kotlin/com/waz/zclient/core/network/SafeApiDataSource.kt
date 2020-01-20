package com.waz.zclient.core.network

import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.exception.DatabaseFailure
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.NetworkFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@Suppress("TooGenericExceptionCaught")
suspend fun <R> requestDatabase(localRequest: suspend () -> R): Either<DatabaseFailure, R> =
    try {
        Either.Right(localRequest())
    } catch (e: Exception) {
        Either.Left(DatabaseError)
    }

suspend fun <R> accessData(
    mainRequest: suspend () -> Either<Failure, R>,
    fallbackRequest: suspend () -> Either<Failure, R>,
    saveToDatabase: suspend (R) -> Unit
): Either<Failure, R> =

    with(mainRequest()) {
        onFailure {
            when (it) {
                is DatabaseError -> performFallback(fallbackRequest, saveToDatabase)
                else -> Timber.e("Database request failed with unknown error ")
            }
        }
    }

private fun <R> performFallback(
    fallbackRequest: suspend () -> Either<Failure, R>,
    saveToDatabase: suspend (R) -> Unit
): Either<Failure, R> =

    runBlocking {
        with(fallbackRequest()) {
            onSuccess { runBlocking { saveToDatabase(it) } }
            onFailure {
                when (it) {
                    is NetworkFailure -> Timber.e("Network request failed with generic error ")
                    else -> Timber.e("Network request failed with unknown error ")
                }
            }
        }
    }
