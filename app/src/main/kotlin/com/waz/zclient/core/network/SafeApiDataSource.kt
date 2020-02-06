package com.waz.zclient.core.network

import android.database.sqlite.SQLiteException
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.exception.DatabaseFailure
import com.waz.zclient.core.exception.DatabaseStateError
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.NetworkFailure
import com.waz.zclient.core.exception.SQLError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.core.logging.Logger
import kotlinx.coroutines.runBlocking

const val TAG = "SafeApiDataSource"

@Suppress("TooGenericExceptionCaught")
suspend fun <R> requestDatabase(localRequest: suspend () -> R): Either<DatabaseFailure, R> =
    try {
        Either.Right(localRequest())
    } catch (e: IllegalStateException) {
        Either.Left(DatabaseStateError)
    } catch (e: SQLiteException) {
        Either.Left(SQLError)
    } catch (e: Exception) {
        //TODO keep finding more exceptions room throws and get rid of this
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
                else -> Logger.error(TAG, "Database request failed with unknown error ")
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
                    is NetworkFailure -> Logger.error(TAG, "Network request failed with generic error ")
                    else -> Logger.error(TAG, "Network request failed with unknown error ")
                }
            }
        }
    }
