package com.waz.zclient.feature.auth.registration.register.usecase

import com.waz.zclient.core.exception.BadRequest
import com.waz.zclient.core.exception.Conflict
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.feature.auth.registration.register.RegisterRepository

class RegisterUseCase(private val registerRepository: RegisterRepository) :
    UseCase<Unit, RegisterParams>() {
    override suspend fun run(params: RegisterParams): Either<Failure, Unit> =
        registerRepository.register(params.name)
            .fold({
                when (it) {
                    is BadRequest -> Either.Left(InvalidActivationCode)
                    is Forbidden -> Either.Left(UnauthorizedEmailOrPhone)
                    is NotFound -> Either.Left(ActivationCodeNotFound)
                    is Conflict -> Either.Left(EmailOrPhoneInUse)
                    else -> Either.Left(it)
                }
            }) { Either.Right(it) }!!
}

data class RegisterParams(val name: String)

object InvalidActivationCode : RegisterFailure()
object UnauthorizedEmailOrPhone : RegisterFailure()
object ActivationCodeNotFound : RegisterFailure()
object EmailOrPhoneInUse : RegisterFailure()

sealed class RegisterFailure : FeatureFailure()
