package com.waz.zclient.features.auth.registration.activation

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface ActivationRepository {
    suspend fun sendEmailActivationCode(email: String): Either<Failure, Unit>
}
