package com.waz.zclient.feature.auth.registration.register.datasources

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.auth.registration.register.RegisterRepository
import com.waz.zclient.feature.auth.registration.register.datasources.remote.RegisterRemoteDataSource

class RegisterDataSource(private val registerRemoteDataSource: RegisterRemoteDataSource) : RegisterRepository {

    override suspend fun register(name: String): Either<Failure, Unit> =
        registerRemoteDataSource.register(name)
}