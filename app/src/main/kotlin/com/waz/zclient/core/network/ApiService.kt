package com.waz.zclient.core.network

import com.waz.zclient.core.exception.BadRequest
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.exception.InternalServerError
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.exception.Unauthorized
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Either.Left
import com.waz.zclient.core.functional.Either.Right
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class ApiService(private val networkHandler: NetworkHandler,
                 private val networkClient: NetworkClient) {

    fun <T> createApi(clazz: Class<T>) = networkClient.create(clazz)

    suspend fun <T> request(call: suspend () -> Response<T>, default: T): Either<Failure, T> =
        withContext(Dispatchers.IO) {
            return@withContext when (networkHandler.isConnected) {
                true -> performRequest(call, default)
                false, null -> Left(NetworkConnection)
            }
        }

    private suspend fun <T> performRequest(call: suspend () -> Response<T>, default: T): Either<Failure, T> {
        return try {
            val response = call()
            when (response.isSuccessful) {
                true -> Right(response.body() ?: default)
                false -> handleRequestError(response)
            }
        } catch (exception: Throwable) {
            //todo: check coroutine exceptions (e.g. Cancelled)
            Left(ServerError)
        }
    }

    private fun <T> handleRequestError(response: Response<T>): Either<Failure, T> {
        return when (response.code()) {
            400 -> Left(BadRequest)
            401 -> Left(Unauthorized)
            403 -> Left(Forbidden)
            404 -> Left(NotFound)
            500 -> Left(InternalServerError)
            else -> Left(ServerError)
        }
    }
}
