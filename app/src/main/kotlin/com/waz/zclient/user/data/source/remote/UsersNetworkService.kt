package com.waz.zclient.user.data.source.remote

import com.google.gson.annotations.SerializedName
import com.waz.zclient.user.data.source.remote.model.UserApi
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface UsersNetworkService {

    companion object {
        private const val BASE = "/self"
        private const val PHONE = "/phone"
        private const val EMAIL = "/email"
        private const val HANDLE = "/handle"
        private const val NAME = "/name"
    }

    @GET(BASE)
    suspend fun profileDetails(): Response<UserApi>

    @PUT("$BASE$NAME")
    suspend fun changeName(@Body name: ChangeNameRequest): Response<Unit>

    @PUT("$BASE$HANDLE")
    suspend fun changeHandle(@Body handle: ChangeHandleRequest): Response<Unit>

    @PUT("$BASE$EMAIL")
    suspend fun changeEmail(@Body email: ChangeEmailRequest): Response<Unit>

    @PUT("$BASE$PHONE")
    suspend fun changePhone(@Body phone: ChangePhoneRequest): Response<Unit>
}

data class ChangeNameRequest(
    @SerializedName("name") val name: String
)

data class ChangeEmailRequest(
    @SerializedName("email") val email: String
)

data class ChangePhoneRequest(
    @SerializedName("phone") val phone: String
)

data class ChangeHandleRequest(
    @SerializedName("handle") val handle: String
)
