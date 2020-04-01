package com.waz.zclient.shared.activation.datasources

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.shared.activation.datasources.remote.ActivationRemoteDataSource
import com.waz.zclient.shared.activation.ActivationRepository

class ActivationDataSource(
    private val activationRemoteDataSource: ActivationRemoteDataSource
) : ActivationRepository {

    override suspend fun sendEmailActivationCode(email: String): Either<Failure, Unit> =
        activationRemoteDataSource.sendEmailActivationCode(email)
}
