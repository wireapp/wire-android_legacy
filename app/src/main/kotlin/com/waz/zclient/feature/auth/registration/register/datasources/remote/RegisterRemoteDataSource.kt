package com.waz.zclient.feature.auth.registration.register.datasources.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler

class RegisterRemoteDataSource(
    private val activationApi: RegisterApi,
    override val networkHandler: NetworkHandler
) : ApiService() {
    suspend fun register(name: String): Either<Failure, UserResponse> =
        request { activationApi.register(RegisterRequestBody(name = name)) }
}
