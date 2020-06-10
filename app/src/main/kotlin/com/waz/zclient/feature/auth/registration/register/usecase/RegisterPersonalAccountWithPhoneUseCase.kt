package com.waz.zclient.feature.auth.registration.register.usecase

import com.waz.zclient.core.exception.Conflict
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.feature.auth.registration.register.RegisterRepository

class RegisterPersonalAccountWithPhoneUseCase(private val registerRepository: RegisterRepository) :
    UseCase<Unit, PhoneRegistrationParams> {
    override suspend fun run(params: PhoneRegistrationParams): Either<Failure, Unit> =
        registerRepository.registerPersonalAccountWithPhone(
            params.name,
            params.phone,
            params.activationCode
        ).fold({
            when (it) {
                is Forbidden -> Either.Left(UnauthorizedPhone)
                is NotFound -> Either.Left(InvalidPhoneActivationCode)
                is Conflict -> Either.Left(PhoneInUse)
                else -> Either.Left(it)
            }
        }) { Either.Right(it) }!!
}

data class PhoneRegistrationParams(
    val name: String,
    val phone: String,
    val activationCode: String
)

object UnauthorizedPhone : RegisterPersonalAccountWithPhoneFailure()
object InvalidPhoneActivationCode : RegisterPersonalAccountWithPhoneFailure()
object PhoneInUse : RegisterPersonalAccountWithPhoneFailure()

sealed class RegisterPersonalAccountWithPhoneFailure : FeatureFailure()
