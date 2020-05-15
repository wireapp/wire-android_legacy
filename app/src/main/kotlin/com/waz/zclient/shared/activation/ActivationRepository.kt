package com.waz.zclient.shared.activation

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface ActivationRepository {
    suspend fun sendEmailActivationCode(email: String): Either<Failure, Unit>
    suspend fun activateEmail(email: String, code: String): Either<Failure, Unit>
}
