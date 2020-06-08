package com.waz.zclient.feature.auth.registration.register.datasources.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler
import java.util.*

class RegisterRemoteDataSource(
    private val registerApi: RegisterApi,
    override val networkHandler: NetworkHandler
) : ApiService() {
    suspend fun registerPersonalAccountWithEmail(
        name: String,
        email: String,
        password: String,
        activationCode: String
    ): Either<Failure, UserResponse> = request {
        registerApi.register(RegisterRequestBody(
            name = name,
            email = email,
            password = password,
            emailCode = activationCode,
            locale = Locale.getDefault().toLanguageTag(),
            label = UUID.randomUUID().toString()
        ))
    }

    suspend fun registerPersonalAccountWithPhone(
        name: String,
        phone: String,
        activationCode: String
    ): Either<Failure, UserResponse> = request {
        registerApi.register(RegisterRequestBody(
            name = name,
            phone = phone,
            phoneCode = activationCode,
            locale = Locale.getDefault().toLanguageTag(),
            label = UUID.randomUUID().toString()
        ))
    }
}
