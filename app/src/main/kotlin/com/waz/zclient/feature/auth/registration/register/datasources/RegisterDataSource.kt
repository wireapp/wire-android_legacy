package com.waz.zclient.feature.auth.registration.register.datasources

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.feature.auth.registration.register.RegisterRepository
import com.waz.zclient.feature.auth.registration.register.datasources.remote.RegisterRemoteDataSource

class RegisterDataSource(
    private val registerRemoteDataSource: RegisterRemoteDataSource
) : RegisterRepository {

    override suspend fun registerPersonalAccountWithEmail(
        name: String,
        email: String,
        password: String,
        activationCode: String
    ): Either<Failure, Unit> = registerRemoteDataSource.registerPersonalAccountWithEmail(
        name,
        email,
        password,
        activationCode
    ).map { Unit }
}
