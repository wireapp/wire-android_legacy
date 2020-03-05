package com.waz.zclient.features.auth.registration.activation

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

class ActivationDataSource(
    private val activationRemoteDataSource: ActivationRemoteDataSource
) : ActivationRepository {

    override suspend fun sendEmailActivationCode(email: String): Either<Failure, Unit> =
        activationRemoteDataSource.sendEmailActivationCode(email)
}
