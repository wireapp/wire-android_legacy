package com.waz.zclient.core.network

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.Failure.ServerError
import com.waz.zclient.core.extension.failFastIfUIThread
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.Either.Left
import com.waz.zclient.core.functional.Either.Right
import retrofit2.Call

class Network {

    fun <T, R> request(call: Call<T>, transform: (T) -> R, default: T): Either<Failure, R> {
        Thread.currentThread().failFastIfUIThread()
        return try {
            val response = call.execute()
            when (response.isSuccessful) {
                true -> Right(transform((response.body() ?: default)))
                false -> Left(ServerError)
            }
        } catch (exception: Throwable) {
            Left(ServerError)
        }
    }
}
