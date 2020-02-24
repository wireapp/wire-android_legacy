package com.waz.zclient.user.domain.usecase.email

import android.util.Patterns
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class ValidateEmailUseCase : UseCase<String, ValidateEmailParams>() {

    override suspend fun run(params: ValidateEmailParams): Either<Failure, String> =
        when {
            !emailCharactersValid(params.email) -> Either.Left(EmailInvalid)
            isEmailTooShort(params.email) -> Either.Left(EmailTooShort)
            else -> Either.Right(params.email)
        }

    private fun emailCharactersValid(email: String) = Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun isEmailTooShort(email: String) =
        email.length < EMAIL_MIN_LENGTH

    companion object {
        private const val EMAIL_MIN_LENGTH = 5
    }
}

data class ValidateEmailParams(val email: String)
