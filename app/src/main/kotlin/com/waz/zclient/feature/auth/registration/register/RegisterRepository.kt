package com.waz.zclient.feature.auth.registration.register

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface RegisterRepository {
    suspend fun registerPersonalAccountWithEmail(
        name: String,
        email: String,
        password: String,
        activationCode: String
    ): Either<Failure, Unit>
}
