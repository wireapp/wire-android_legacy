package com.waz.zclient.user.data.source.remote

import com.google.gson.annotations.SerializedName
import com.waz.zclient.user.data.source.remote.model.UserApi
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface UsersNetworkService {

    companion object {
        private const val SELF = "/self"
        private const val PHONE = "/phone"
        private const val EMAIL = "/email"
        private const val HANDLE = "/handle"
        private const val NAME = "/name"
    }

    @GET(SELF)
    suspend fun profileDetails(): Response<UserApi>

    @PUT("$SELF$NAME")
    suspend fun changeName(@Body name: ChangeNameRequest): Response<Unit>

    @PUT("$SELF$HANDLE")
    suspend fun changeHandle(@Body handle: ChangeHandleRequest): Response<Unit>

    @PUT("$SELF$EMAIL")
    suspend fun changeEmail(@Body email: ChangeEmailRequest): Response<Unit>

    @PUT("$SELF$PHONE")
    suspend fun changePhone(@Body phone: ChangePhoneRequest): Response<Unit>

    @PUT("$SELF")
    suspend fun changeAccentColor(@Body accentColorId: ChangeAccentColorRequest): Response<Unit>
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

data class ChangeAccentColorRequest(
    @SerializedName("accent_id") val accentId: Int
)
