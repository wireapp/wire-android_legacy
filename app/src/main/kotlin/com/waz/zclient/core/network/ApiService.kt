package com.waz.zclient.core.network

import com.waz.zclient.core.exception.BadRequest
import com.waz.zclient.core.exception.EmptyResponseBody
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.exception.InternalServerError
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.exception.Unauthorized
import com.waz.zclient.core.functional.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

abstract class ApiService {
    abstract val networkHandler: NetworkHandler

    suspend fun <T> request(default: T? = null, call: suspend () -> Response<T>): Either<Failure, T> =
        withContext(Dispatchers.IO) {
            return@withContext when (networkHandler.isConnected) {
                true -> performRequest(call, default)
                false, null -> Either.Left(NetworkConnection)
            }
        }

    private suspend fun <T> performRequest(call: suspend () -> Response<T>, default: T? = null): Either<Failure, T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                response.body()?.let { Either.Right(it) }
                    ?: (default?.let { Either.Right(it) } ?: Either.Left(EmptyResponseBody))
            } else {
                handleRequestError(response)
            }
        } catch (exception: Throwable) {
            //todo: check coroutine exceptions (e.g. Cancelled)
            Either.Left(ServerError)
        }
    }

    private fun <T> handleRequestError(response: Response<T>): Either<Failure, T> {
        return when (response.code()) {
            400 -> Either.Left(BadRequest)
            401 -> Either.Left(Unauthorized)
            403 -> Either.Left(Forbidden)
            404 -> Either.Left(NotFound)
            500 -> Either.Left(InternalServerError)
            else -> Either.Left(ServerError)
        }
    }
}
