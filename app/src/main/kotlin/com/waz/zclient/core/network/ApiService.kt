package com.waz.zclient.core.network

import com.waz.zclient.core.exception.BadRequest
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.exception.InternalServerError
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.exception.Unauthorized
import com.waz.zclient.core.extension.failFastIfUIThread
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Either.Left
import com.waz.zclient.core.functional.Either.Right
import retrofit2.Call
import retrofit2.Response

abstract class ApiService(private val networkHandler: NetworkHandler) {

    fun <T> request(call: Call<T>, default: T): Either<Failure, T> {
        Thread.currentThread().failFastIfUIThread()

        return when (networkHandler.isConnected) {
            true -> performRequest(call, default)
            false, null -> Left(NetworkConnection)
        }
    }

    private fun <T> performRequest(call: Call<T>, default: T): Either<Failure, T> {
        return try {
            val response = call.execute()
            when (response.isSuccessful) {
                true -> Right(response.body() ?: default)
                false -> handleRequestError(response)
            }
        } catch (exception: Throwable) {
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
