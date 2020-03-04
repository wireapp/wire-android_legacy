package com.waz.zclient.auth.registration.activation

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class SendEmailActivationCodeUseCase(private val activationRepository: ActivationRepository) :
    UseCase<Unit, SendEmailActivationCodeParams>() {
    override suspend fun run(params: SendEmailActivationCodeParams): Either<Failure, Unit> =
        activationRepository.sendEmailActivationCode(params.email)
}

data class SendEmailActivationCodeParams(val email: String)

object InvalidEmail : SendActivationCodeFailure()
object EmailBlackListed : SendActivationCodeFailure()
object EmailInUse : SendActivationCodeFailure()
object ActivationCodeSent : SendActivationCodeSuccess()

sealed class SendActivationCodeSuccess
sealed class SendActivationCodeFailure : FeatureFailure()
