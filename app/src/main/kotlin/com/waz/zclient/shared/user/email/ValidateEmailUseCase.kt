package com.waz.zclient.shared.user.email

import androidx.core.util.PatternsCompat
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class ValidateEmailUseCase : UseCase<Unit, ValidateEmailParams>() {

    override suspend fun run(params: ValidateEmailParams): Either<Failure, Unit> =
        when {
            !emailCharactersValid(params.email) -> Either.Left(EmailInvalid)
            isEmailTooShort(params.email) -> Either.Left(EmailTooShort)
            else -> Either.Right(Unit)
        }

    private fun emailCharactersValid(email: String) =
        PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()

    private fun isEmailTooShort(email: String) =
        email.length < EMAIL_MIN_LENGTH

    companion object {
        private const val EMAIL_MIN_LENGTH = 5
    }
}

data class ValidateEmailParams(val email: String)
