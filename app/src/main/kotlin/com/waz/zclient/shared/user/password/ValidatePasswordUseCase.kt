package com.waz.zclient.shared.user.password

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class ValidatePasswordUseCase : UseCase<Unit, ValidatePasswordParams>() {

    override suspend fun run(params: ValidatePasswordParams): Either<Failure, Unit> =
        when {
            isPasswordTooShort(params.password, params.minLength) -> Either.Left(PasswordTooShort)
            isPasswordTooLong(params.password, params.maxLength) -> Either.Left(PasswordTooLong)
            !passwordContainsLowercaseLetter(params.password) -> Either.Left(NoLowerCaseLetter)
            !passwordContainsUppercaseLetter(params.password) -> Either.Left(NoUpperCaseLetter)
            !passwordContainsDigit(params.password) -> Either.Left(NoDigit)
            !passwordContainsSpecialCharacter(params.password) -> Either.Left(NoSpecialCharacter)
            else -> Either.Right(Unit)
        }

    private fun isPasswordTooShort(password: String, minLength: Int) =
        password.length < minLength

    private fun isPasswordTooLong(password: String, maxLength: Int) =
        password.length > maxLength

    private fun passwordContainsLowercaseLetter(password: String) =
        password.contains(Regex(LOWERCASE_PATTERN))

    private fun passwordContainsUppercaseLetter(password: String) =
        password.contains(Regex(UPPERCASE_PATTERN))

    private fun passwordContainsDigit(password: String) =
        password.contains(Regex(DIGIT_PATTERN))

    private fun passwordContainsSpecialCharacter(password: String) =
        password.contains(Regex(SPECIAL_CHARACTER_PATTERN))

    companion object {
        private const val LOWERCASE_PATTERN = "[a-z]"
        private const val UPPERCASE_PATTERN = "[A-Z]"
        private const val DIGIT_PATTERN = "[0-9]"
        private const val SPECIAL_CHARACTER_PATTERN = "[^a-zA-Z0-9]"
    }
}

data class ValidatePasswordParams(val password: String, val minLength: Int, val maxLength: Int)

object PasswordTooShort : ValidatePasswordFailure()
object PasswordTooLong : ValidatePasswordFailure()
object NoLowerCaseLetter : ValidatePasswordFailure()
object NoUpperCaseLetter : ValidatePasswordFailure()
object NoDigit : ValidatePasswordFailure()
object NoSpecialCharacter : ValidatePasswordFailure()

sealed class ValidatePasswordFailure : FeatureFailure()
