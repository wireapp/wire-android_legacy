package com.waz.zclient.core.network

import com.waz.zclient.core.exception.*
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Either.Left
import com.waz.zclient.core.functional.Either.Right
import com.waz.zclient.core.threading.ThreadHandler
import retrofit2.Call
import retrofit2.Response

class ApiService(private val networkHandler: NetworkHandler,
                 private val threadHandler: ThreadHandler,
                 private val networkClient: NetworkClient,
                 private val rawResponseRegistry: RawResponseRegistry) {

    fun <T> createApi(clazz: Class<T>) = networkClient.create(clazz)

    fun <T> request(call: Call<T>, default: T): Either<Failure, T> {
        require(!threadHandler.isUIThread())

        return when (networkHandler.isConnected) {
            true -> performRequest(call, default)
            false, null -> Left(NetworkConnection)
        }
    }

    private fun <T> performRequest(call: Call<T>, default: T): Either<Failure, T> {
        return try {
            val response = call.execute()
            when (response.isSuccessful) {
                true -> {
                    rawResponseRegistry.notifyRawResponseReceived(response)
                    Right(response.body() ?: default)
                }
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
