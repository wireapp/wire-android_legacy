package com.waz.zclient.user.domain.usecase.handle

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.getOrElse
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.UsersRepository

object HandleTooLongError : ValidateHandleState()
object HandleTooShortError : ValidateHandleState()
object HandleInvalidError : ValidateHandleState()
object HandleExistsAlreadyError : ValidateHandleState()
object HandleIsAvailable : ValidateHandleState()

sealed class ValidateHandleState : FeatureFailure()

class ValidateHandleUseCase(private val usersRepository: UsersRepository)
    : UseCase<String, ValidateHandleParams>() {

    companion object {
        private const val HANDLE_MAX_LENGTH = 21
        private const val HANDLE_MIN_LENGTH = 2
        private val HANDLE_REGEX = """^([a-z]|[0-9]|_)*""".toRegex()
    }

    override suspend fun run(params: ValidateHandleParams): Either<Failure, String> =
        if (handleAvailable(params.newHandle) is HandleIsAvailable) {
            isHandleValid(params.newHandle)
        } else {
            Either.Left(HandleExistsAlreadyError)
        }

    private suspend fun handleAvailable(newHandle: String)
        = usersRepository.doesHandleExist(newHandle).getOrElse(HandleIsAvailable)

    private fun isHandleValid(newHandle: String): Either<Failure, String> =
        if (!newHandle.matches(HANDLE_REGEX)) {
            Either.Left(HandleInvalidError)
        } else {
            when {
                newHandle.length > HANDLE_MAX_LENGTH -> Either.Left(HandleTooLongError)
                newHandle.length < HANDLE_MIN_LENGTH -> Either.Left(HandleTooShortError)
                else -> Either.Right(newHandle)
            }
        }
}

data class ValidateHandleParams(val newHandle: String)
