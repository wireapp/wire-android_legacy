package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.Failure.NoNetworkConnection
import com.waz.zclient.core.exception.Failure.ServerError
import com.waz.zclient.core.extension.failFastIfUIThread
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Either.Left
import com.waz.zclient.core.functional.Either.Right
import retrofit2.Call
import retrofit2.Response

open class ApiService(private val networkHandler: NetworkHandler) {

    fun <T> request(call: Call<T>, default: T): Either<Failure, T> {
        Thread.currentThread().failFastIfUIThread()

        return when (networkHandler.isConnected) {
            true -> performRequest(call, default)
            false, null -> Left(NoNetworkConnection)
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
        return when (response.errorBody() != null) {
            true -> Left(ServerError) //TODO: Treat different error types coming from the server.
            false -> Left(ServerError)
        }
    }
}
