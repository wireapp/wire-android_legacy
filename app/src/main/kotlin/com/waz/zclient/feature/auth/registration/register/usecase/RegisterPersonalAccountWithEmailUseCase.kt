package com.waz.zclient.feature.auth.registration.register.usecase

import com.waz.zclient.core.exception.Conflict
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.feature.auth.registration.register.RegisterRepository

class RegisterPersonalAccountWithEmailUseCase(private val registerRepository: RegisterRepository) :
    UseCase<Unit, RegistrationParams>() {
    override suspend fun run(params: RegistrationParams): Either<Failure, Unit> =
        registerRepository.registerPersonalAccountWithEmail(
            params.name,
            params.email,
            params.password,
            params.activationCode
        ).fold({
            when (it) {
                is Forbidden -> Either.Left(UnauthorizedEmail)
                is NotFound -> Either.Left(InvalidActivationCode)
                is Conflict -> Either.Left(EmailInUse)
                else -> Either.Left(it)
            }
        }) { Either.Right(it) }!!
}

data class RegistrationParams(
    val name: String,
    val email: String,
    val password: String,
    val activationCode: String
)

object UnauthorizedEmail : RegisterPersonalAccountWithEmailFailure()
object InvalidActivationCode : RegisterPersonalAccountWithEmailFailure()
object EmailInUse : RegisterPersonalAccountWithEmailFailure()

sealed class RegisterPersonalAccountWithEmailFailure : FeatureFailure()
