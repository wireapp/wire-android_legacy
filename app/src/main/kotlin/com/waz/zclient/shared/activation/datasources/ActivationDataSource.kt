package com.waz.zclient.shared.activation.datasources

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.shared.activation.ActivationRepository
import com.waz.zclient.shared.activation.datasources.remote.ActivationRemoteDataSource

class ActivationDataSource(private val activationRemoteDataSource: ActivationRemoteDataSource) : ActivationRepository {

    override suspend fun sendEmailActivationCode(email: String): Either<Failure, Unit> =
        activationRemoteDataSource.sendEmailActivationCode(email)

    override suspend fun activateEmail(email: String, code: String): Either<Failure, Unit> =
        activationRemoteDataSource.activateEmail(email, code)
}
