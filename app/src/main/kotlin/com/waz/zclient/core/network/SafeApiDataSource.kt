package com.waz.zclient.core.network

import android.database.sqlite.SQLiteException
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.exception.DatabaseFailure
import com.waz.zclient.core.exception.DatabaseStateError
import com.waz.zclient.core.exception.SQLError
import com.waz.zclient.core.functional.Either

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
