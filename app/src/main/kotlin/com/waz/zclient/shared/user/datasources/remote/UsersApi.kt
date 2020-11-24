package com.waz.zclient.shared.user.datasources.remote

import retrofit2.Response
import retrofit2.http.GET

interface UsersApi {

    @GET(SELF)
    suspend fun profileDetails(): Response<UserResponse>

    companion object {
        private const val SELF = "/self"
    }
}
