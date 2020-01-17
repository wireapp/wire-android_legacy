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
import com.waz.zclient.core.threading.ThreadHandler
import retrofit2.Call
import retrofit2.Response

class ApiService(
    private val networkHandler: NetworkHandler,
    private val threadHandler: ThreadHandler,
    private val networkClient: NetworkClient
) {

    fun <T> createApi(clazz: Class<T>) = networkClient.create(clazz)

    fun <T> request(call: Call<T>, default: T): Either<Failure, T> {
        require(!threadHandler.isUIThread())

        return when (networkHandler.isConnected) {
            true -> performRequest(call, default)
            false, null -> Left(NetworkConnection)
        }
    }

    @Suppress("TooGenericExceptionCaught")
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
            CODE_BAD_REQUEST -> Left(BadRequest)
            CODE_UNAUTHORIZED -> Left(Unauthorized)
            CODE_FORBIDDEN -> Left(Forbidden)
            CODE_NOT_FOUND -> Left(NotFound)
            CODE_INTERNAL_SERVER_ERROR -> Left(InternalServerError)
            else -> Left(ServerError)
        }
    }

    companion object {
        private const val CODE_BAD_REQUEST = 400
        private const val CODE_UNAUTHORIZED = 401
        private const val CODE_FORBIDDEN = 403
        private const val CODE_NOT_FOUND = 404
        private const val CODE_INTERNAL_SERVER_ERROR = 500
    }
}
