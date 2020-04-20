package com.waz.zclient.shared.activation.datasources.remote

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.core.network.NetworkHandler

class ActivationRemoteDataSource(
    private val activationApi: ActivationApi,
    override val networkHandler: NetworkHandler
) : ApiService() {
    suspend fun sendEmailActivationCode(email: String): Either<Failure, Unit> =
        request { activationApi.sendActivationCode(SendActivationCodeRequest(email = email)) }

    suspend fun activateEmail(email: String, code: String): Either<Failure, Unit> =
        request { activationApi.activate(ActivationRequest(email = email, code = code, dryrun = true)) }
}
