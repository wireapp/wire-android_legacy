package com.waz.zclient.user.data.source.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestApi
import com.waz.zclient.user.data.source.remote.model.UserApi
import org.json.JSONObject

class UsersRemoteDataSource constructor(private val usersNetworkService: UsersNetworkService) {

    companion object {
        private const val NAME_REQUEST_BODY_KEY = "name"
        private const val EMAIL_REQUEST_BODY_KEY = "email"
        private const val HANDLE_REQUEST_BODY_KEY = "handle"
        private const val PHONE_REQUEST_BODY_KEY = "phone"
    }

    suspend fun profileDetails(): Either<Failure, UserApi> = requestApi { usersNetworkService.profileDetails() }

    suspend fun changeName(name: String): Either<Failure, Any> = requestApi { usersNetworkService.changeName(buildRequestBody(NAME_REQUEST_BODY_KEY, name)) }

    suspend fun changeHandle(handle: String): Either<Failure, Any> = requestApi { usersNetworkService.changeHandle(buildRequestBody(HANDLE_REQUEST_BODY_KEY, handle)) }

    suspend fun changeEmail(email: String): Either<Failure, Any> = requestApi { usersNetworkService.changeEmail(buildRequestBody(EMAIL_REQUEST_BODY_KEY, email)) }

    suspend fun changePhone(phone: String): Either<Failure, Any> = requestApi { usersNetworkService.changePhone(buildRequestBody(PHONE_REQUEST_BODY_KEY, phone)) }

    private fun buildRequestBody(key: String, value: String) = JSONObject().also { it.put(key, value) }
}
