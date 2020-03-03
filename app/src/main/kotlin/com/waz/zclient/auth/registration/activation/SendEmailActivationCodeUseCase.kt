package com.waz.zclient.auth.registration.activation

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class SendEmailActivationCodeUseCase(private val activationRepository: ActivationRepository) :
    UseCase<Any, SendActivationCodeParams>() {

    override suspend fun run(params: SendActivationCodeParams): Either<Failure, Any> =
        activationRepository.sendEmailActivationCode(params.email)

}

data class SendActivationCodeParams(val email: String)
